package software.aws.clientrt.serde.xml

import software.aws.clientrt.logging.Logger
import software.aws.clientrt.serde.*

// Represents aspects of SdkFieldDescriptor that are particular to the Xml format
sealed class FieldLocation {
    // specifies the mapping to a sdk field index
    abstract val fieldIndex: Int

    data class Text(override val fieldIndex: Int) : FieldLocation() // Xml nodes have only one associated Text element
    data class Attribute(override val fieldIndex: Int, val name: XmlToken.QualifiedName) : FieldLocation()
}

/**
 * Provides a deserialiser for XML documents
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 */
// TODO ~ make internal before GA
class XmlDeserializer(private val reader: XmlStreamReader) : Deserializer {
    constructor(input: ByteArray) : this(xmlStreamReader(input))
    private val logger = Logger.getLogger<XmlDeserializer>()
    private var firstStructCall = true

    override suspend fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        logger.debug { "Deserializing struct $descriptor under ${reader.lastToken}" }

        if (firstStructCall) {
            if (!descriptor.hasTrait<XmlSerialName>()) throw DeserializerStateException("Top-level struct $descriptor requires a XmlSerialName trait but has none.")

            firstStructCall = false

            reader.nextToken() // Consume the start element of top-level struct

            val structToken = reader.seek<XmlToken.BeginElement>() ?: throw DeserializerStateException("Could not find a begin element for new struct")
            val objectName = descriptor.toQualifiedName()
            if (structToken.name != objectName) throw DeserializerStateException("Expected begin element of $objectName but found ${structToken::class.qualifiedName} of ${structToken.name}")
        }

        // Consume any remaining terminating tokens from previous deserialization
        reader.seek<XmlToken.BeginElement>()

        // Because attributes set on the root node of the struct, we must read the values before creating the subtree
        val attribFields = reader.tokenAttributesToFieldLocations(descriptor)
        val parentToken = reader.lastToken as XmlToken.BeginElement
        return XmlStructDeserializer(descriptor, reader.subTreeReader(), parentToken, attribFields)
    }

    override suspend fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        logger.debug { "Deserializing list $descriptor under ${reader.lastToken}" }

        val depth = when (descriptor.hasTrait<Flattened>()) {
            true -> XmlStreamReader.DepthSpecifier.CURRENT
            else -> XmlStreamReader.DepthSpecifier.CHILD
        }

        return XmlListDeserializer(reader.subTreeReader(depth), descriptor)
    }

    override suspend fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        logger.debug { "Deserializing map $descriptor under ${reader.lastToken}" }

        return XmlMapDeserializer(reader.subTreeReader(XmlStreamReader.DepthSpecifier.CURRENT), descriptor)
    }
}

/**
 * Deserializes specific XML structures into forms that can produce Maps
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param descriptor associated [SdkFieldDescriptor] which represents the expected Map
 * @param primitiveDeserializer used to deserialize primitive values
 */
internal class XmlMapDeserializer(
    private val reader: XmlStreamReader,
    private val descriptor: SdkFieldDescriptor,
    private val primitiveDeserializer: PrimitiveDeserializer =  XmlPrimitiveDeserializer(reader, descriptor)
) : PrimitiveDeserializer by primitiveDeserializer, Deserializer.EntryIterator {
    private val mapTrait = descriptor.findTrait<XmlMapName>() ?: XmlMapName.DEFAULT

    override suspend fun hasNextEntry(): Boolean {
        // Seek to either the entry or key token depending on the flatness of the map
        val nextEntryToken = when(descriptor.hasTrait<Flattened>()) {
            true -> reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.key }
            false -> reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.entry }
        }

        return nextEntryToken != null
    }

    override suspend fun key(): String {
        // Seek to the key begin token
        reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.key }
            ?: error("Unable to find key $mapTrait.key in $descriptor")

        val keyValueToken = reader.takeNextAs<XmlToken.Text>()
        reader.nextToken() // Consume the end wrapper

        return keyValueToken.value ?: throw DeserializerStateException("Key unspecified in $descriptor")
    }

    override suspend fun nextHasValue(): Boolean {
        // Expect a begin and value (or another begin) token if Map entry has a value
        val peekBeginToken = reader.peek(1) ?: throw DeserializerStateException("Unexpected termination of token stream in $descriptor")
        val peekValueToken = reader.peek(2) ?: throw DeserializerStateException("Unexpected termination of token stream in $descriptor")

        return peekBeginToken !is XmlToken.EndElement && peekValueToken !is XmlToken.EndElement
    }
}

/**
 * Deserializes specific XML structures into forms that can produce Lists
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param descriptor associated [SdkFieldDescriptor] which represents the expected Map
 * @param primitiveDeserializer used to deserialize primitive values
 */
internal class XmlListDeserializer(
    private val reader: XmlStreamReader,
    private val descriptor: SdkFieldDescriptor,
    private val primitiveDeserializer: PrimitiveDeserializer =  XmlPrimitiveDeserializer(reader, descriptor)
) : PrimitiveDeserializer by primitiveDeserializer, Deserializer.ElementIterator {
    private var firstCall = true
    private val flattened = descriptor.hasTrait<Flattened>()
    private val elementName = (descriptor.findTrait<XmlCollectionName>() ?: XmlCollectionName.DEFAULT).element

    override suspend fun hasNextElement(): Boolean {
        if (!flattened && firstCall) {
            val nextToken = reader.peek()
            val matchedListDescriptor = nextToken is XmlToken.BeginElement && nextToken.name == descriptor.toQualifiedName()
            val hasChildren = if (nextToken == null) false else nextToken.depth >= reader.lastToken!!.depth

            if (!matchedListDescriptor && !hasChildren) return false

            // Discard the wrapper and move to the first element in the list
            if (matchedListDescriptor) reader.nextToken()

            firstCall = false
        }

        if (flattened && descriptor.hasTrait<XmlSerialName>()) {
            // This is a special case in which a flattened list is passed in a nested struct.
            // Because our subtree is not CHILD, we cannot rely on the subtree boundary to determine end of collection.
            // Rather we search for either the next begin token matching the list member name, or the terminal token of the list struct.
            val tokens = listOfNotNull(reader.lastToken, reader.peek())

            // Iterate over the token stream until begin token matching name is found or end element matching list is found.
            return tokens
                .filterIsInstance<XmlToken.BeginElement>()
                .any { it.name.local == descriptor.serialName.name }
        } else {
            // If we can find another begin token w/ the element name, we have more elements to process
            return reader.seek<XmlToken.BeginElement> { it.name.local == elementName }.isNotTerminal()
        }
    }

    override suspend fun nextHasValue(): Boolean = reader.peek() !is XmlToken.EndElement
}

/**
 * Deserializes specific XML structures into forms that can produce structures
 *
 * @param objDescriptor associated [SdkObjectDescriptor] which represents the expected structure
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param parentToken initial token of associated structure
 * @param parsedFieldLocations list of [FieldLocation] representing values able to be loaded into deserialized instances
 */
internal class XmlStructDeserializer(
    private val objDescriptor: SdkObjectDescriptor,
    private val reader: XmlStreamReader,
    private val parentToken: XmlToken.BeginElement,
    private val parsedFieldLocations: MutableList<FieldLocation> = mutableListOf()
) : Deserializer.FieldIterator {
    // Used to track direct deserialization or further nesting between calls to findNextFieldIndex() and deserialize<Type>()
    private var reentryFlag: Boolean = false

    override suspend fun findNextFieldIndex(): Int? {
        if (inNestedMode()) {
            // Returning from a nested struct call.  Nested deserializer consumed
            // tokens so clear them here to avoid processing stale state
            parsedFieldLocations.clear()
        }

        if (parsedFieldLocations.isEmpty()) {
            val matchedFieldLocations = when (val nextToken = reader.nextToken()) {
                null, is XmlToken.EndDocument -> return null
                is XmlToken.EndElement -> return findNextFieldIndex()
                is XmlToken.BeginElement -> objDescriptor.fields
                        .filter { objDescriptor.fieldTokenMatcher(it, nextToken) }  // Filter out fields with different serialName
                        .mapNotNull { it.findLocation(nextToken, reader.peek() ?: return@mapNotNull null) }
                else -> return findNextFieldIndex()
            }

            // Sorting ensures attribs are processed before text, as processing the Text token pushes the parser on to the next token.
            parsedFieldLocations.addAll(matchedFieldLocations.sortedBy { it is FieldLocation.Text })
        }

        return when {
            parsedFieldLocations.isNotEmpty() -> parsedFieldLocations.first().fieldIndex
            else -> {
                skipValue() // Move to the next peer element in the input document. This enables skipping unrecognized content.
                // If we are still parsing within the bounds of the (sub)tree, continue
                if (reader.peek().isNotTerminal()) findNextFieldIndex() else null
            }
        }
    }

    private suspend fun <T> deserializeValue(transform: ((String) -> T)): T {
        reentryFlag = false
        if (parsedFieldLocations.isEmpty()) throw DeserializationException("matchedFields is empty, was findNextFieldIndex() called?")

        return when (val nextField = parsedFieldLocations.removeFirst()) {
            is FieldLocation.Text -> {
                val value = when (val peekToken = reader.peek()) {
                    is XmlToken.Text -> reader.takeNextAs<XmlToken.Text>().value ?: ""
                    is XmlToken.EndElement -> ""
                    else -> throw DeserializerStateException("Unexpected token $peekToken")
                }
                transform(value)
            }
            is FieldLocation.Attribute -> {
                transform(parentToken.attributes[nextField.name]
                        ?: throw DeserializerStateException("Expected attrib value ${nextField.name} not found in ${parentToken.name}")
                )
            }
        }
    }

    override suspend fun skipValue() { reader.skipNext() }

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

    // A struct deserializer can be called in two "modes":
    // 1. to deserialize a value.  This calls findNextFieldIndex() followed by deserialize<SomePrimitiveType>()
    // 2. to deserialize a nested container. This calls findNextFieldIndex() followed by a call to another deserialize<Struct/List/Map>()
    // Because state is built in findNextFieldIndex() that is intended to be used directly in deserialize<SomePrimitiveType>() (mode 1)
    // and there is no explicit way that this type knows which mode is in use, the state built must be cleared.
    // this is done by flipping a bit between the two calls.  If the bit has not been flipped on any call to findNextFieldIndex()
    // it is determined that the nested mode was used and any existing state should be cleared.
    // if the state is not cleared, deserialization goes into an infinite loop because the deserializer sees pending fields to pull from the stream.
    private fun inNestedMode(): Boolean {
        return when (reentryFlag) {
            true -> true
            false -> { reentryFlag = true; false }
        }
    }
}

// Extract the attributes from the last-read token and match them to [FieldLocation] on the [SdkObjectDescriptor].
private suspend fun XmlStreamReader.tokenAttributesToFieldLocations(descriptor: SdkObjectDescriptor): MutableList<FieldLocation> =
    if (descriptor.hasXmlAttributes && lastToken is XmlToken.BeginElement) {
        descriptor.fields
            .filter { it.hasTrait<XmlAttribute>() }
            .filter { it.findLocation(lastToken as XmlToken.BeginElement, peek() ?: error("WTF")) != null }
            .map { FieldLocation.Attribute(it.index, it.toQualifiedName() ?: error("WTF")) }
            .toMutableList()
    } else {
        mutableListOf()
    }

// Returns a [FieldLocation] if the field maps to the current token
private fun SdkFieldDescriptor.findLocation(currentToken: XmlToken.BeginElement, nextToken: XmlToken): FieldLocation? {
    return when (val property = toFieldLocation()) {
        is FieldLocation.Text -> {
            when {
                nextToken is XmlToken.Text -> property
                nextToken is XmlToken.BeginElement -> property
                // The following allows for struct primitives to remain unvisited if no value
                // but causes nested deserializers to be called even if they contain no value
                nextToken is XmlToken.EndElement && currentToken.name == nextToken.name -> property
                else -> null
            }
        }
        is FieldLocation.Attribute -> {
            if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
        }
    }
}

// Produce a [FieldLocation] type based on presence of traits of field
// A field without an attribute trait is assumed to be a text token
private fun SdkFieldDescriptor.toFieldLocation(): FieldLocation =
    when (val attributeTrait = findTrait<XmlAttribute>()) {
        null -> FieldLocation.Text(index) // Assume a text value if no attributes defined.
        else -> FieldLocation.Attribute(index, toQualifiedName() ?: error("WTF"))
    }

// Returns true if the SerialKind holds other values
private val SerialKind.isContainer: Boolean
    get() = when (this) {
        is SerialKind.Map, SerialKind.List, SerialKind.Struct -> true
        else -> false
    }

// Matches fields and tokens with matching qualified name
private fun SdkObjectDescriptor.fieldTokenMatcher(fieldDescriptor: SdkFieldDescriptor, beginElement: XmlToken.BeginElement): Boolean {
    if (fieldDescriptor.kind == SerialKind.List && fieldDescriptor.hasTrait<Flattened>()) {
        val fieldName = fieldDescriptor.findTrait<XmlCollectionName>() ?: XmlCollectionName.DEFAULT
        val tokenQname = beginElement.name

        // It may be that we are matching a flattened list element or matching a list itself.  In the latter
        // case the following predicate will not work, so if we fail to match the member
        // try again (below) to match against the container.
        if (fieldName.element == tokenQname.local) return true
    }

    val fieldQname = fieldDescriptor.toQualifiedName(findTrait())
    val tokenQname = beginElement.name
    val matchLocalAndNoPrefix = fieldQname?.local == tokenQname.local && fieldQname?.ns?.prefix == null && tokenQname?.ns?.prefix == null

    return fieldQname == tokenQname || matchLocalAndNoPrefix
}

// Return the next token of the specified type or throw [DeserializerStateException] if incorrect type.
internal suspend inline fun <reified TExpected : XmlToken> XmlStreamReader.takeNextAs(): TExpected {
    val token = this.nextToken() ?: throw DeserializerStateException("Expected ${TExpected::class} but instead found null")
    requireToken<TExpected>(token)
    return token as TExpected
}

// require that the given token be of type [TExpected] or else throw an exception
internal inline fun <reified TExpected> requireToken(token: XmlToken) {
    if (token::class != TExpected::class) {
        throw DeserializerStateException("expected ${TExpected::class}; found ${token::class} ($token)")
    }
}
