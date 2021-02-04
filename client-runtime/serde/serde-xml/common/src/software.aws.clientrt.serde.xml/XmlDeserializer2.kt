package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlDeserializer2(private val reader: XmlStreamReader) : Deserializer {

    private var rootStructDeserializer: StructDeserializer? = null

    constructor(input: ByteArray) : this(xmlStreamReader(input))

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        return when (rootStructDeserializer) {
            null -> {
                val structSerializer = StructDeserializer(descriptor, reader, currentToken = reader.peekTo<XmlToken.BeginElement>())
                rootStructDeserializer = structSerializer
                structSerializer
            }
            else -> {
                // Flush existing token to avoid revisiting same node upon return
                rootStructDeserializer!!.clearNodeProperties()

                // Optionally consume next token until we match our objectDescriptor.
                // This can vary depending on where deserializeStruct() is called from (list/map vs struct)
                var token = rootStructDeserializer!!.currentToken
                val targetTokenName = descriptor.expectTrait<XmlSerialName>().name
                while(token.qualifiedName.name != targetTokenName) token = reader.takeNextToken()

                StructDeserializer(descriptor, reader, currentToken = token)
            }
        }
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        check(rootStructDeserializer != null) { "List cannot be deserialized independently from a parent struct" }
        return ListDeserializer(descriptor, reader, rootStructDeserializer!!)
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        check(rootStructDeserializer != null) { "Map cannot be deserialized independently from a parent struct" }
        return MapDeserializer(descriptor, reader, rootStructDeserializer!!)
    }

    /**
     * Deserialize a byte value defined as the text section of an Xml element.
     *
     */
    override fun deserializeByte(): Byte = rootStructDeserializer?.deserializeByte() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeInt(): Int = rootStructDeserializer?.deserializeInt() ?: error("No serializer available")

    /**
     * Deserialize a short value defined as the text section of an Xml element.
     */
    override fun deserializeShort(): Short = rootStructDeserializer?.deserializeShort() ?: error("No serializer available")

    /**
     * Deserialize a long value defined as the text section of an Xml element.
     */
    override fun deserializeLong(): Long = rootStructDeserializer?.deserializeLong() ?: error("No serializer available")

    /**
     * Deserialize an float value defined as the text section of an Xml element.
     */
    override fun deserializeFloat(): Float = rootStructDeserializer?.deserializeFloat() ?: error("No serializer available")

    /**
     * Deserialize a double value defined as the text section of an Xml element.
     */
    override fun deserializeDouble(): Double = rootStructDeserializer?.deserializeDouble() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeString(): String = rootStructDeserializer?.deserializeString() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeBoolean(): Boolean = rootStructDeserializer?.deserializeBoolean() ?: error("No serializer available")


    override fun deserializeNull(): Nothing? {
        TODO("Not yet implemented")
    }

    override fun nextHasValue(): Boolean {
        TODO("Not yet implemented")
    }

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

    class StructDeserializer(
        private val objDescriptor: SdkObjectDescriptor,
        private val reader: XmlStreamReader,
        private val parsedNodeTokens: MutableList<XmlNodeValueToken> = mutableListOf(),
        var currentToken: XmlToken.BeginElement
    ) : Deserializer.FieldIterator {

        data class FieldTokenMapping(val field: SdkFieldDescriptor, val token: XmlToken.BeginElement)

        init {
            val qualifiedName = objDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
            check(currentToken.qualifiedName.name == qualifiedName.name) { "Expected name ${qualifiedName.name} but found ${currentToken.qualifiedName.name}" }
            if (objDescriptor.findTrait<XmlNamespace>()?.isDefault() == true) { // If a default namespace is set, verify that the serialized form matches obj descriptor
                check(currentToken.qualifiedName.namespaceUri == objDescriptor.findTrait<XmlNamespace>()?.uri) { "Expected name ${objDescriptor.findTrait<XmlNamespace>()?.uri} but found ${currentToken.qualifiedName.namespaceUri}" }
            }
        }

        override fun findNextFieldIndex(): Int? {
            if (parsedNodeTokens.isEmpty()) {
                // if the current node has nothing more to deserialize, take the next token from the stream.
                val nodeValueTokens = when (val nextToken = reader.nextToken()) {
                    is XmlToken.BeginElement -> {
                        // for each Node, produce zero or more [XmlNodeValueToken]s that map
                        // to literal values from the incoming token stream
                        currentToken = nextToken
                        // Collect all elements of a node that may be used to populate a response type
                        objDescriptor.fields
                            .map { sdkFieldDescriptor -> FieldTokenMapping(sdkFieldDescriptor, nextToken) }
                            .filter(::fieldTokenMatcher) // Filter out fields with different serialName
                            .mapNotNull { (fieldDescriptor, token) ->
                                // Load all NodeProperties with values from field descriptors in fieldToNodeIndex
                                val nodePropertyOption = fieldDescriptor.toNodePropertyIfValue(token, reader.peek())
                                if (nodePropertyOption == null && reader.peek() is XmlToken.EndElement) {
                                    // Consume nodes without values
                                    reader.nextToken()
                                }
                                nodePropertyOption
                            }
                    }
                    is XmlToken.EndDocument,
                    is XmlToken.EndElement -> return null // this should always signify object serialization is complete
                    else -> error("Unexpected token $nextToken")
                }

                nodeValueTokens.sortedBy { it is XmlNodeValueToken.Attribute }
                parsedNodeTokens.addAll(nodeValueTokens)
            }

            return when {
                parsedNodeTokens.isNotEmpty() -> parsedNodeTokens.first().fieldIndex
                else -> findNextFieldIndex()
            }
        }

        override fun skipValue() {
            TODO("Not yet implemented")
        }

        fun clearNodeProperties() = parsedNodeTokens.clear()

        private fun <T> deserializeValue(transform: ((String) -> T)): T {
            check(parsedNodeTokens.isNotEmpty()) { "fieldToNodeIndex is empty, was findNextFieldIndex() called?" }

            val value = when (val nextNode = parsedNodeTokens.removeFirst()) {
                is XmlNodeValueToken.Text -> {
                    val token = reader.takeNextToken<XmlToken.Text>()
                    token.value?.let { transform(it) } ?: error("wtf")
                }
                is XmlNodeValueToken.Attribute -> {
                    transform(currentToken.attributes[nextNode.name] ?: error("WFT"))
                }
            }

            if (parsedNodeTokens.isEmpty()) reader.takeUntil<XmlToken.EndElement>()

            return value
        }

        override fun nextHasValue(): Boolean {
            TODO("Not yet implemented")
        }

        /**
         * Deserialize a byte value defined as the text section of an Xml element.
         *
         */
        override fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte()?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize an integer value defined as the text section of an Xml element.
         */
        override fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize a short value defined as the text section of an Xml element.
         */
        override fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort()?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize a long value defined as the text section of an Xml element.
         */
        override fun deserializeLong(): Long = deserializeValue { it.toLongOrNull()?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize an float value defined as the text section of an Xml element.
         */
        override fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull()?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize a double value defined as the text section of an Xml element.
         */
        override fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull()?: throw DeserializationException("Unable to deserialize $it") }

        /**
         * Deserialize an integer value defined as the text section of an Xml element.
         */
        override fun deserializeString(): String = deserializeValue { it }

        /**
         * Deserialize an integer value defined as the text section of an Xml element.
         */
        override fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

        override fun deserializeNull(): Nothing? {
            TODO("adf")
        }

        // Matches fields and tokens with matching qualified name
        private fun fieldTokenMatcher(fieldTokenMapping: FieldTokenMapping): Boolean {
            val fieldQname = fieldTokenMapping.field.serialName.toQualifiedName(objDescriptor.findTrait())
            val tokenQname = fieldTokenMapping.token.qualifiedName
            //println("desc: $fieldQname token: ${fieldTokenMapping.token.qualifiedName}")
            return fieldQname == tokenQname
        }
    }
}

class ListDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlDeserializer2.StructDeserializer,
    private val startLevel: Int = reader.currentDepth()
) : Deserializer.ElementIterator {

    override fun hasNextElement(): Boolean {
        // Check the next token, if it is the corresponding end node to the start, exit.
        val nextToken = reader.peek()

        if (nextToken is XmlToken.EndElement) {
            parentDeserializer.clearNodeProperties()
            //Depending on flat/not-flat, may need to pull of multiple end nodes
            while(reader.currentDepth() > startLevel) reader.takeNextToken<XmlToken.EndElement>()
            return false
        }

        return true
    }

    private fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (reader.peek() is XmlToken.BeginElement) {
            // In the case of flattened lists, we "fall" into the first node as there is no wrapper.
            // this conditional checks that case for the first element of the list.
            val listElementToken = reader.takeNextToken<XmlToken.BeginElement>()
            // It's unclear if list elements model XML namespaces. For now match only node name.
            if (listElementToken.qualifiedName.name != fieldDescriptor.expectTrait<XmlList>().elementName) {
                //Depending on flat/not-flat, may need to consume multiple start nodes
                return deserializeValue(transform)
            }
        }

        val token = reader.takeNextToken<XmlToken.Text>()

        return token.value?.let { transform(it) }?.also {
            reader.takeNextToken<XmlToken.EndElement>()
        } ?: error("wtf")
    }

    /**
     * Deserialize a byte value defined as the text section of an Xml element.
     *
     */
    override fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a short value defined as the text section of an Xml element.
     */
    override fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a long value defined as the text section of an Xml element.
     */
    override fun deserializeLong(): Long = deserializeValue { it.toLongOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an float value defined as the text section of an Xml element.
     */
    override fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a double value defined as the text section of an Xml element.
     */
    override fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeString(): String = deserializeValue { it }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override fun deserializeNull(): Nothing? {
        reader.takeNextToken<XmlToken.EndElement>()
        return null
    }

    override fun nextHasValue(): Boolean {
        return when (reader.peek()) {
            is XmlToken.EndElement,
            is XmlToken.EndDocument -> false
            is XmlToken.BeginElement -> {
                // Here we need to read the next token so we can peek the next to determine if there is a value.
                // deserializeValue() can conditionally handle start or value nodes
                reader.takeNextToken<XmlToken.BeginElement>()

                when (reader.peek()) {
                    is XmlToken.EndElement,
                    is XmlToken.EndDocument -> false
                    else -> true
                }
            }
            else -> true
        }

    }
}

class MapDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlDeserializer2.StructDeserializer,
    private val startLevel: Int = reader.currentDepth()
) : Deserializer.EntryIterator {
    override fun hasNextEntry(): Boolean {
        // Check the next token, if it is the corresponding end node to the start, exit.
        val nextToken = reader.peek()

        if (nextToken is XmlToken.EndElement) {
            parentDeserializer.clearNodeProperties()
            //Depending on flat/not-flat, may need to pull of multiple end nodes
            while(reader.currentDepth() > startLevel) reader.takeNextToken<XmlToken.EndElement>()
            return false
        }

        return true
    }

    override fun key(): String {
        var nextToken = reader.takeNextToken<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()

        return if (mapTrait.flattened) {
            if (nextToken.qualifiedName.name == fieldDescriptor.expectTrait<XmlSerialName>().name) nextToken = reader.takeNextToken()

            check(nextToken.qualifiedName.name == mapTrait.keyName) { "Expected ${mapTrait.keyName} representing key of map, but found ${nextToken.qualifiedName}" }
            val keyValue = reader.takeNextToken<XmlToken.Text>()
            check(keyValue.value != null && keyValue.value.isNotBlank()) { "Key entry is empty." }
            check(reader.takeNextToken<XmlToken.EndElement>().qualifiedName.name == "key") { "Expected end of key field" }
            keyValue.value
        } else {
            check(nextToken.qualifiedName.name == mapTrait.entry) { "Expected token representing entry of map, but found ${nextToken.qualifiedName}" }
            val keyToken = reader.takeNextToken<XmlToken.BeginElement>()
            // See https://awslabs.github.io/smithy/1.0/spec/core/model.html#map
            check(keyToken.qualifiedName.name == mapTrait.keyName) { "Expected key field, but found ${keyToken.qualifiedName}" }
            val keyValue = reader.takeNextToken<XmlToken.Text>()
            check(keyValue.value != null && keyValue.value.isNotBlank()) { "Key entry is empty." }
            check(reader.takeNextToken<XmlToken.EndElement>().qualifiedName.name == mapTrait.keyName) { "Expected end of key field" }
            keyValue.value
        }
    }

    private fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (reader.peek() is XmlToken.BeginElement) {
            // In the case of flattened lists, we "fall" into the first node as there is no wrapper.
            // this conditional checks that case for the first element of the list.
            val listElementToken = reader.takeNextToken<XmlToken.BeginElement>()
            // It's unclear if list elements model XML namespaces. For now match only node name.
            if (listElementToken.qualifiedName.name != fieldDescriptor.expectTrait<XmlList>().elementName) {
                //Depending on flat/not-flat, may need to consume multiple start nodes
                return deserializeValue(transform)
            }
        }

        val token = reader.takeNextToken<XmlToken.Text>()

        return token.value?.let { transform(it) }?.also {
            reader.takeNextToken<XmlToken.EndElement>()

            //Optionally consume the entry wrapper
            val mapTrait = fieldDescriptor.expectTrait<XmlMap>()
            val nextToken = reader.peek()
            if (nextToken is XmlToken.EndElement) {
                val consumeEndToken = when (mapTrait.flattened) {
                    true -> nextToken.qualifiedName.name == fieldDescriptor.expectTrait<XmlSerialName>().name
                    false -> nextToken.qualifiedName.name == mapTrait.entry
                }
                if (consumeEndToken) reader.takeNextToken<XmlToken.EndElement>()
            }
        } ?: error("wtf")
    }

    override fun nextHasValue(): Boolean {
        val valueWrapperToken = reader.takeNextToken<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()
        check(valueWrapperToken.qualifiedName.name == mapTrait.valueName) { "Expected map value name but found ${valueWrapperToken.qualifiedName}" }

        return reader.peek() is XmlToken.Text
    }

    override fun deserializeNull(): Nothing? {
        reader.takeNextToken<XmlToken.EndElement>()
        return null
    }

    /**
     * Deserialize a byte value defined as the text section of an Xml element.
     *
     */
    override fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a short value defined as the text section of an Xml element.
     */
    override fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a long value defined as the text section of an Xml element.
     */
    override fun deserializeLong(): Long = deserializeValue { it.toLongOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an float value defined as the text section of an Xml element.
     */
    override fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize a double value defined as the text section of an Xml element.
     */
    override fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull()?: throw DeserializationException("Unable to deserialize $it") }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeString(): String = deserializeValue { it }

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }
}

private fun XmlAttribute.toQualifiedName(): XmlToken.QualifiedName = XmlToken.QualifiedName(name, namespace)

private inline fun <reified TExpected : XmlToken> XmlStreamReader.takeUntil(): TExpected {
    var token = this.nextToken()
    while (token::class != TExpected::class && token !is XmlToken.EndDocument) {
        token = this.nextToken()
    }

    if (token::class != TExpected::class) error("Did not find ${TExpected::class}")
    return token as TExpected
}

private inline fun <reified TExpected : XmlToken> XmlStreamReader.peekTo(): TExpected {
    var token = this.peek()
    while (token::class != TExpected::class && token !is XmlToken.EndDocument) {
        token = this.nextToken()
    }

    if (token::class != TExpected::class) error("Did not find ${TExpected::class}")
    return token as TExpected
}

private inline fun <reified TExpected : XmlToken> XmlStreamReader.takeNextToken(): TExpected {
    val token = this.nextToken()
    requireToken<TExpected>(token)
    return token as TExpected
}

// require that the given token be of type [TExpected] or else throw an exception
private inline fun <reified TExpected> requireToken(token: XmlToken) {
    if (token::class != TExpected::class) {
        throw DeserializerStateException("expected ${TExpected::class}; found ${token::class}")
    }
}

// Produce a [XmlNodeValueToken] type based on presence of traits of field
// A field without an attribute trait is assumed to be a text token
private fun SdkFieldDescriptor.toNodeValueToken(): XmlDeserializer2.XmlNodeValueToken =
    when (val attributeTrait = findTrait<XmlAttribute>()) {
        null -> XmlDeserializer2.XmlNodeValueToken.Text(index) // Assume a text value if no attributes defined.
        else -> XmlDeserializer2.XmlNodeValueToken.Attribute(index, attributeTrait.toQualifiedName())
    }

private fun SdkFieldDescriptor.toNodePropertyIfValue(currentToken: XmlToken.BeginElement, nextToken: XmlToken): XmlDeserializer2.XmlNodeValueToken? {
    return when (val property = toNodeValueToken()) {
        is XmlDeserializer2.XmlNodeValueToken.Text -> {
            when {
                nextToken is XmlToken.Text && nextToken.value?.isNotBlank() == true -> property
                nextToken is XmlToken.BeginElement -> property
                else -> null
            }
        }
        is XmlDeserializer2.XmlNodeValueToken.Attribute -> {
            if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
        }
    }
}