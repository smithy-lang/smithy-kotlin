package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * Describes a type that maps to a sdk field index
 */
interface NodeField {
    val fieldIndex: Int
}

/**
 * Represents a "sub" token in any given Xml node.  Xml stream readers
 * do not represent attributes as tokens.  For any given node traversed,
 * we can extract more than one value from that node based on it's ability to contain
 * an arbitrary number of attributes along with a text value.
 */
sealed class XmlNodeValueToken : NodeField {
    data class Text(override val fieldIndex: Int) : XmlNodeValueToken() // Xml nodes have only one associated Text element
    data class Attribute(override val fieldIndex: Int, val name: XmlToken.QualifiedName) : XmlNodeValueToken()
}

/**
 * Deserializes a structure
 */
class XmlStructDeserializer(
    private val objDescriptor: SdkObjectDescriptor,
    private val reader: XmlStreamReader,
    private val parsedNodeTokens: MutableList<XmlNodeValueToken> = mutableListOf(),
    private val startLevel: Int = reader.currentDepth
) : Deserializer.FieldIterator {

    private val currentNode: XmlToken.BeginElement

    // Represents a mapping from field descriptor to an xml node.
    data class FieldTokenMapping(val field: SdkFieldDescriptor, val token: XmlToken.BeginElement)

    init {
        // Validate inputs
        val qualifiedName = objDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
        currentNode = reader.currentToken as XmlToken.BeginElement
        if (currentNode.qualifiedName.name != qualifiedName.name) throw DeserializerStateException("Expected name ${qualifiedName.name} but found ${currentNode.qualifiedName.name}")
        if (objDescriptor.findTrait<XmlNamespace>()?.isDefault() == true && currentNode.qualifiedName.namespaceUri != objDescriptor.findTrait<XmlNamespace>()?.uri) {
            // If a default namespace is set, verify that the serialized form matches obj descriptor
            throw DeserializerStateException("Expected name ${objDescriptor.findTrait<XmlNamespace>()?.uri} but found ${currentNode.qualifiedName.namespaceUri}")
        }
    }

    override suspend fun findNextFieldIndex(): Int? {
        if (parsedNodeTokens.isEmpty()) {
            // if the current node has nothing more to deserialize, take the next token from the stream.
            val nodeValueTokens = when (val nextToken = reader.takeNextToken()) {
                is XmlToken.BeginElement -> {
                    // for each Node, produce zero or more [XmlNodeValueToken]s that map
                    // to literal values from the incoming token stream
                    // Collect all elements of a node that may be used to populate a response type
                    objDescriptor.fields
                        .map { sdkFieldDescriptor -> FieldTokenMapping(sdkFieldDescriptor, nextToken) }
                        .filter(::fieldTokenMatcher) // Filter out fields with different serialName
                        .mapNotNull { (fieldDescriptor, token) ->
                            // Load all NodeProperties with values from field descriptors in fieldToNodeIndex
                            val nodePropertyOption = fieldDescriptor.findNodeValueTokenForField(token, reader.peekNextToken())
                            if (reader.peekNextToken() is XmlToken.EndElement && nodePropertyOption == null) {
                                // Consume nodes without values
                                reader.takeNextToken()
                                return findNextFieldIndex()
                            }
                            nodePropertyOption
                        }
                }
                is XmlToken.EndDocument -> return null
                is XmlToken.EndElement -> {
                    return when {
                        // Explicitly match the end node
                        reader.currentDepth == startLevel && nextToken.qualifiedName == currentNode.qualifiedName -> null
                        // Traverse children looking for matches to fields
                        reader.currentDepth >= startLevel -> findNextFieldIndex()
                        // We have left the node, exit
                        else -> null
                    }
                }
                else -> throw DeserializerStateException("Unexpected token $nextToken")
            }

            parsedNodeTokens.addAll(nodeValueTokens.sortedBy { it is XmlNodeValueToken.Text })
        }

        return when {
            parsedNodeTokens.isNotEmpty() -> parsedNodeTokens.first().fieldIndex
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
     * Clear any existing [XmlNodeValueToken]s. This is necessary when codegen deserializers dive into children.
     */
    fun clearNodeValueTokens() = parsedNodeTokens.clear()

    // Based on the top [XmlNodeValueToken], deserialize a text or attribute value.
    private fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (parsedNodeTokens.isEmpty()) throw DeserializationException("fieldToNodeIndex is empty, was findNextFieldIndex() called?")

        val value = when (val nextNode = parsedNodeTokens.removeFirst()) {
            is XmlNodeValueToken.Text -> {
                if (parsedNodeTokens.any { it is XmlNodeValueToken.Attribute }) throw DeserializationException("Text tokens should always be consumed last")
                val token = reader.takeNextOf<XmlToken.Text>()
                token.value?.let { transform(it) } ?: throw DeserializerStateException("Expected value in node ${currentNode.qualifiedName}")
            }
            is XmlNodeValueToken.Attribute -> {
                val currentNode = reader.currentToken as XmlToken.BeginElement
                transform(
                    currentNode.attributes[nextNode.name]
                        ?: throw DeserializerStateException("Expected attribute value ${nextNode.name} not found in node ${currentNode.qualifiedName}")
                )
            }
        }

        if (parsedNodeTokens.isEmpty()) reader.takeUntil<XmlToken.EndElement>()

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
        reader.takeNextOf<XmlToken.EndElement>()
        return null
    }

    // Matches fields and tokens with matching qualified name
    private fun fieldTokenMatcher(fieldTokenMapping: FieldTokenMapping): Boolean {
        val fieldQname = fieldTokenMapping.field.serialName.toQualifiedName(objDescriptor.findTrait())
        val tokenQname = fieldTokenMapping.token.qualifiedName
        return fieldQname == tokenQname
    }
}

private fun XmlAttribute.toQualifiedName(): XmlToken.QualifiedName = XmlToken.QualifiedName(name, namespace)

// Produce a [XmlNodeValueToken] type based on presence of traits of field
// A field without an attribute trait is assumed to be a text token
private fun SdkFieldDescriptor.toNodeValueToken(): XmlNodeValueToken =
    when (val attributeTrait = findTrait<XmlAttribute>()) {
        null -> XmlNodeValueToken.Text(index) // Assume a text value if no attributes defined.
        else -> XmlNodeValueToken.Attribute(index, attributeTrait.toQualifiedName())
    }

// Returns a [XmlNodeValueToken] if the field maps to the current node
internal fun SdkFieldDescriptor.findNodeValueTokenForField(currentToken: XmlToken.BeginElement, nextToken: XmlToken): XmlNodeValueToken? {
    return when (val property = toNodeValueToken()) {
        is XmlNodeValueToken.Text -> {
            when {
                nextToken is XmlToken.Text -> property
                nextToken is XmlToken.BeginElement -> property
                // The following allows for struct primitives to remain unvisited if no value
                // but causes nested deserializers to be called even if they contain no value
                nextToken is XmlToken.EndElement &&
                    currentToken.qualifiedName == nextToken.qualifiedName &&
                    this.kind.container -> property
                else -> null
            }
        }
        is XmlNodeValueToken.Attribute -> {
            if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
        }
    }
}
