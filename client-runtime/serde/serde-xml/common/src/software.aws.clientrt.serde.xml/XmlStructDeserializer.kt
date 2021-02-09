package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * Represents the location of a value in any given Xml node.  Xml stream readers
 * do not represent attributes as tokens.  For any given node traversed,
 * we can extract more than one value from that node based on it's ability to contain
 * an arbitrary number of attributes along with a text value.
 */
internal sealed class FieldLocation {
    // specifies the mapping to a sdk field index
    abstract val fieldIndex: Int

    data class Text(override val fieldIndex: Int) : FieldLocation() // Xml nodes have only one associated Text element
    data class Attribute(override val fieldIndex: Int, val name: XmlToken.QualifiedName) : FieldLocation()
}

/**
 * Deserializes a structure
 */
// TODO - mark class internal and remove integration tests once serde is stable
class XmlStructDeserializer(
    private val objDescriptor: SdkObjectDescriptor,
    private val reader: XmlStreamReader
) : Deserializer.FieldIterator {

    private val parsedFields: MutableList<FieldLocation> = mutableListOf()
    private val startLevel: Int = reader.currentDepth
    private val startNode: XmlToken.BeginElement

    init {
        // Validate inputs
        val qualifiedName = objDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
        startNode = reader.currentToken as XmlToken.BeginElement
        if (startNode.qualifiedName.name != qualifiedName.name) throw DeserializerStateException("Expected name ${qualifiedName.name} but found ${startNode.qualifiedName.name}")
        if (objDescriptor.findTrait<XmlNamespace>()?.isDefault() == true && startNode.qualifiedName.namespaceUri != objDescriptor.findTrait<XmlNamespace>()?.uri) {
            // If a default namespace is set, verify that the serialized form matches obj descriptor
            throw DeserializerStateException("Expected name ${objDescriptor.findTrait<XmlNamespace>()?.uri} but found ${startNode.qualifiedName.namespaceUri}")
        }
    }

    override suspend fun findNextFieldIndex(): Int? {
        if (parsedFields.isEmpty()) {
            // if the current node has nothing more to deserialize, take the next token from the stream.
            val nodeValueTokens = when (val nextToken = reader.nextToken()) {
                is XmlToken.BeginElement -> {
                    // for each Node, produce zero or more [FieldLocation]s that map
                    // to literal values from the incoming token stream
                    // Collect all elements of a node that may be used to populate a response type
                    objDescriptor.fields
                        .filter { fieldTokenMatcher(it, nextToken) } // Filter out fields with different serialName
                        .mapNotNull { fieldDescriptor ->
                            // Load all [FieldLocation]s with values from field descriptors
                            val location = fieldDescriptor.findLocation(nextToken, reader.peek())
                            if (reader.peek() is XmlToken.EndElement && location == null) {
                                // Consume nodes without values
                                reader.nextToken()
                                return findNextFieldIndex()
                            }
                            location
                        }
                }
                is XmlToken.EndDocument -> return null
                is XmlToken.EndElement -> {
                    return when {
                        // Explicitly match the end node
                        reader.currentDepth == startLevel && nextToken.qualifiedName == startNode.qualifiedName -> null
                        // Traverse children looking for matches to fields
                        reader.currentDepth >= startLevel -> findNextFieldIndex()
                        // We have left the node, exit
                        else -> null
                    }
                }
                else -> throw DeserializerStateException("Unexpected token $nextToken")
            }

            parsedFields.addAll(nodeValueTokens.sortedBy { it is FieldLocation.Text })
        }

        return when {
            parsedFields.isNotEmpty() -> parsedFields.first().fieldIndex
            else -> {
                skipValue() // Move to the next node
                // If we are still parsing within the bounds of the start node, continue.
                // Otherwise exit
                if (reader.currentDepth >= startLevel) findNextFieldIndex() else null
            }
        }
    }

    override suspend fun skipValue() = reader.skipNext()

    /**
     * Clear any existing [FieldLocation]s. This is necessary when codegen deserializers dive into children.
     */
    fun clearNodeValueTokens() = parsedFields.clear()

    // Based on the top [FieldLocation], deserialize a text or attribute value.
    private suspend fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (parsedFields.isEmpty()) throw DeserializationException("fieldToNodeIndex is empty, was findNextFieldIndex() called?")

        val value = when (val nextNode = parsedFields.removeFirst()) {
            is FieldLocation.Text -> {
                if (parsedFields.any { it is FieldLocation.Attribute }) throw DeserializationException("Text tokens should always be consumed last")
                val token = reader.takeNextAs<XmlToken.Text>()
                token.value?.let { transform(it) } ?: throw DeserializerStateException("Expected value in node ${startNode.qualifiedName}")
            }
            is FieldLocation.Attribute -> {
                val currentNode = reader.currentToken as XmlToken.BeginElement
                transform(
                    currentNode.attributes[nextNode.name]
                        ?: throw DeserializerStateException("Expected attribute value ${nextNode.name} not found in node ${currentNode.qualifiedName}")
                )
            }
        }

        if (parsedFields.isEmpty()) reader.takeUntil<XmlToken.EndElement>()

        return value
    }

    override suspend fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeLong(): Long = deserializeValue { it.toLongOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override suspend fun deserializeString(): String = deserializeValue { it }

    override suspend fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override suspend fun deserializeNull(): Nothing? {
        reader.takeNextAs<XmlToken.EndElement>()
        return null
    }

    // Matches fields and tokens with matching qualified name
    private fun fieldTokenMatcher(fieldDescriptor: SdkFieldDescriptor, beginElement: XmlToken.BeginElement): Boolean {
        val fieldQname = fieldDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
        val tokenQname = beginElement.qualifiedName
        return fieldQname == tokenQname
    }

    // Produce a [FieldLocation] type based on presence of traits of field
// A field without an attribute trait is assumed to be a text token
    private fun SdkFieldDescriptor.toFieldLocation(): FieldLocation =
        when (val attributeTrait = findTrait<XmlAttribute>()) {
            null -> FieldLocation.Text(index) // Assume a text value if no attributes defined.
            else -> FieldLocation.Attribute(index, attributeTrait.toQualifiedName())
        }

    // Returns a [FieldLocation] if the field maps to the current node
    private fun SdkFieldDescriptor.findLocation(currentToken: XmlToken.BeginElement, nextToken: XmlToken): FieldLocation? {
        return when (val property = toFieldLocation()) {
            is FieldLocation.Text -> {
                when {
                    nextToken is XmlToken.Text -> property
                    nextToken is XmlToken.BeginElement -> property
                    // The following allows for struct primitives to remain unvisited if no value
                    // but causes nested deserializers to be called even if they contain no value
                    nextToken is XmlToken.EndElement &&
                        currentToken.qualifiedName == nextToken.qualifiedName &&
                        this.kind.isContainer -> property
                    else -> null
                }
            }
            is FieldLocation.Attribute -> {
                if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
            }
        }
    }

    // Returns true if the SerialKind holds other values
    private val SerialKind.isContainer: Boolean
        get() = when (this) {
            is SerialKind.Map, SerialKind.List, SerialKind.Struct -> true
            else -> false
        }

    private fun XmlAttribute.toQualifiedName(): XmlToken.QualifiedName = XmlToken.QualifiedName(name, namespace)
}
