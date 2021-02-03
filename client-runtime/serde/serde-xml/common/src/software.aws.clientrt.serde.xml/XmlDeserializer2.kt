package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlDeserializer2(private val reader: XmlStreamReader) : Deserializer {

    private var rootStructDeserializer: XmlFieldIterator3? = null

    constructor(input: ByteArray) : this(xmlStreamReader(input))

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        val structDeserializer = XmlFieldIterator3(descriptor, reader, currentToken = reader.peek() as XmlToken.BeginElement)
        rootStructDeserializer = structDeserializer
        return structDeserializer
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        check(rootStructDeserializer != null) { "List cannot be deserialized independently from a parent struct" }
        return XmlListFieldIterator3(descriptor, reader, rootStructDeserializer!!)
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        check(rootStructDeserializer != null) { "Map cannot be deserialized independently from a parent struct" }
        return XmlListEntryIterator3(descriptor, reader, rootStructDeserializer!!)
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

    sealed class NodeProperty : NodeField {
        data class XmlTextValue(override val fieldIndex: Int) :
            NodeProperty() // Xml nodes have only one associated Text element

        data class XmlAttributeValue(override val fieldIndex: Int, val name: XmlToken.QualifiedName) : NodeProperty()
    }

    class XmlListFieldIterator3(
        private val fieldDescriptor: SdkFieldDescriptor,
        private val reader: XmlStreamReader,
        private val parentDeserializer: XmlFieldIterator3,
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

    class XmlFieldIterator3(
        private val objDescriptor: SdkObjectDescriptor,
        private val reader: XmlStreamReader,
        private val parsedNodeProperties: MutableList<NodeProperty> = mutableListOf(),
        var currentToken: XmlToken.BeginElement
    ) : Deserializer.FieldIterator {

        init {
            val qualifiedName = objDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
            check(currentToken.qualifiedName.name == qualifiedName.name) { "Expected name ${qualifiedName.name} but found ${currentToken.qualifiedName.name}" }
            if (objDescriptor.findTrait<XmlNamespace>()?.isDefault() == true) { // If a default namespace is set, verify that the serialized form matches obj descriptor
                check(currentToken.qualifiedName.namespaceUri == objDescriptor.findTrait<XmlNamespace>()?.uri) { "Expected name ${objDescriptor.findTrait<XmlNamespace>()?.uri} but found ${currentToken.qualifiedName.namespaceUri}" }
            }
        }

        override fun findNextFieldIndex(): Int? {
            if (parsedNodeProperties.isEmpty()) {
                // if the current node has nothing more to deserialize, take the next node.
                when (val nextToken = reader.nextToken()) {
                    is XmlToken.EndDocument,
                    is XmlToken.EndElement -> return null // this should always signify object serialization is complete
                    is XmlToken.BeginElement -> {
                        // Collect all elements of a node that may be used to populate a response type
                        objDescriptor.fields
                            .filter { fieldDescriptor ->
                                // Only look at fields matching serialName
                                val qname = fieldDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
                                println("desc: $qname token: ${nextToken.qualifiedName}")
                                qname == nextToken.qualifiedName
                            }
                            .sortedBy { it.traits.isEmpty() } // Hackish way of putting Text nodes at the bottom
                            .forEach { sdkFieldDescriptor ->
                                // Load all NodeProperties with values from field descriptors in fieldToNodeIndex
                                val nodePropertyOption = sdkFieldDescriptor.toNodePropertyIfValue(reader, nextToken)

                                when {
                                    nodePropertyOption != null -> parsedNodeProperties.add(nodePropertyOption)
                                    else -> {
                                        // If we encountered an empty node, remove the end
                                        if (reader.peek() is XmlToken.EndElement) reader.nextToken()
                                    }
                                }
                            }.also {
                                // Hold necessary state from here to a deserializeXXX() call
                                currentToken = nextToken
                            }
                    }
                }
            }

            return if (parsedNodeProperties.isNotEmpty()) {
                parsedNodeProperties.first().fieldIndex
            } else {
                findNextFieldIndex()
            }
        }

        override fun skipValue() {
            TODO("Not yet implemented")
        }

        fun clearNodeProperties() = parsedNodeProperties.clear()

        private fun <T> deserializeValue(transform: ((String) -> T)): T {
            check(parsedNodeProperties.isNotEmpty()) { "fieldToNodeIndex is empty, was findNextFieldIndex() called?" }

            val value = when (val nextNode = parsedNodeProperties.removeFirst()) {
                is NodeProperty.XmlTextValue -> {
                    val token = reader.takeNextToken<XmlToken.Text>()
                    token.value?.let { transform(it) } ?: error("wtf")
                }
                is NodeProperty.XmlAttributeValue -> {
                    transform(currentToken.attributes[nextNode.name] ?: error("WFT"))
                }
            }

            if (parsedNodeProperties.isEmpty()) reader.takeUntil<XmlToken.EndElement>()

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
    }
}

class XmlListEntryIterator3(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlDeserializer2.XmlFieldIterator3,
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

private fun SdkFieldDescriptor.toNodeProperty(): XmlDeserializer2.NodeProperty {
    return when {
        traits.any { it is XmlAttribute } -> {
            val trait = expectTrait<XmlAttribute>()
            XmlDeserializer2.NodeProperty.XmlAttributeValue(index, trait.toQualifiedName())
        }
        else -> XmlDeserializer2.NodeProperty.XmlTextValue(index) // Assume a text value if no attributes defined.
    }
}

private fun SdkFieldDescriptor.toNodePropertyIfValue(reader: XmlStreamReader, currentToken: XmlToken.BeginElement): XmlDeserializer2.NodeProperty? {
    return when (val property = toNodeProperty()) {
        is XmlDeserializer2.NodeProperty.XmlTextValue -> {
            val nextToken = reader.peek()
            when {
                nextToken is XmlToken.Text && nextToken.value?.isNotBlank() == true -> property
                nextToken is XmlToken.BeginElement /* maybe more here */ -> property
                else -> null
            }
        }
        is XmlDeserializer2.NodeProperty.XmlAttributeValue -> {
            if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
        }
    }
}