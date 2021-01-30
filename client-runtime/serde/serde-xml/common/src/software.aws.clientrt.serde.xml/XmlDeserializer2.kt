package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlDeserializer2(private val reader: XmlStreamReader) : Deserializer {

    private var activeDeserializer: PrimitiveDeserializer? = null

    constructor(input: ByteArray) : this(xmlStreamReader(input))

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        val structDeserializer = XmlFieldIterator3(descriptor, reader)
        activeDeserializer = structDeserializer
        return structDeserializer
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        TODO("Not yet implemented")
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        TODO("Not yet implemented")
    }

    /**
     * Deserialize a byte value defined as the text section of an Xml element.
     *
     */
    override fun deserializeByte(): Byte = activeDeserializer?.deserializeByte() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeInt(): Int = activeDeserializer?.deserializeInt() ?: error("No serializer available")

    /**
     * Deserialize a short value defined as the text section of an Xml element.
     */
    override fun deserializeShort(): Short = activeDeserializer?.deserializeShort() ?: error("No serializer available")

    /**
     * Deserialize a long value defined as the text section of an Xml element.
     */
    override fun deserializeLong(): Long = activeDeserializer?.deserializeLong() ?: error("No serializer available")

    /**
     * Deserialize an float value defined as the text section of an Xml element.
     */
    override fun deserializeFloat(): Float = activeDeserializer?.deserializeFloat() ?: error("No serializer available")

    /**
     * Deserialize a double value defined as the text section of an Xml element.
     */
    override fun deserializeDouble(): Double = activeDeserializer?.deserializeDouble() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */
    override fun deserializeString(): String = activeDeserializer?.deserializeString() ?: error("No serializer available")

    /**
     * Deserialize an integer value defined as the text section of an Xml element.
     */

    override fun deserializeBoolean(): Boolean = activeDeserializer?.deserializeBoolean() ?: error("No serializer available")


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

    class XmlFieldIterator3(
        private val objDescriptor: SdkObjectDescriptor,
        private val reader: XmlStreamReader,
        private val fieldToNodeIndex: MutableList<NodeProperty> = mutableListOf(),
        private var currentToken: XmlToken.BeginElement? = null
    ) : Deserializer.FieldIterator {

        init {
            val token = reader.nextToken()
            check(token is XmlToken.BeginElement) { "Expected XmlToken.BeginElement but found ${token::class.qualifiedName}" }
            val qname = objDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
            check(token.id.name == qname.name) { "Expected name ${qname.name} but found ${token.id.name}" }
            if (objDescriptor.findTrait<XmlNamespace>()?.isDefault() == true) { // If a default namespace is set, verify that the serialized form matches obj descriptor
                check(token.id.namespaceUri == objDescriptor.findTrait<XmlNamespace>()?.uri) { "Expected name ${objDescriptor.findTrait<XmlNamespace>()?.uri} but found ${token.id.namespaceUri}" }
            }
        }

        override fun findNextFieldIndex(): Int? {
            if (fieldToNodeIndex.isEmpty()) {
                // if the current node has nothing more to deserialize, take the next node.
                when (val nextToken = reader.nextToken()) {
                    is XmlToken.EndDocument -> error("Unexpected end to XML token stream.")
                    is XmlToken.EndElement -> return null // this should always signify object serialization is complete
                    is XmlToken.BeginElement -> {
                        // Collect all elements of a node that may be used to populate a response type
                        objDescriptor.fields
                            .filter { fieldDescriptor ->
                                // Only look at fields matching serialName
                                val qname = fieldDescriptor.serialName.toQualifiedName(objDescriptor.findTrait())
                                println("desc: $qname token: ${nextToken.id}")
                                qname == nextToken.id
                            }
                            .sortedBy { it.traits.isEmpty() } // Hackish way of putting Text nodes at the bottom
                            .forEach { sdkFieldDescriptor ->
                                // Load all NodeProperties with values from field descriptors in fieldToNodeIndex
                                sdkFieldDescriptor.toNodePropertyIfValue(reader, nextToken)?.let { fieldToNodeIndex.add(it) }
                            }.also {
                                // Hold necessary state from here to a deserializeXXX() call
                                currentToken = nextToken
                            }
                    }
                }
            }

            return if (fieldToNodeIndex.isNotEmpty()) {
                fieldToNodeIndex.first().fieldIndex
            } else {
                reader.takeUntil<XmlToken.EndElement>()
                findNextFieldIndex()
            }
        }

        override fun skipValue() {
            TODO("Not yet implemented")
        }

        private fun <T> deserializeValue(transform: ((String) -> T)): T {
            check(fieldToNodeIndex.isNotEmpty()) { "fieldToNodeIndex is empty, was findNextFieldIndex() called?" }

            val value = when (val nextNode = fieldToNodeIndex.removeFirst()) {
                is NodeProperty.XmlTextValue -> {
                    val token = reader.takeNextToken<XmlToken.Text>()
                    token.value?.let { transform(it) } ?: error("wtf")
                }
                is NodeProperty.XmlAttributeValue -> {
                    transform(currentToken?.attributes?.get(nextNode.name) ?: error("WFT"))
                }
            }

            if (fieldToNodeIndex.isEmpty()) reader.takeUntil<XmlToken.EndElement>()

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
            if (nextToken is XmlToken.Text && nextToken.value?.isNotBlank() == true) property else null
        }
        is XmlDeserializer2.NodeProperty.XmlAttributeValue -> {
            if (currentToken.attributes[property.name]?.isNotBlank() == true) property else null
        }
    }
}