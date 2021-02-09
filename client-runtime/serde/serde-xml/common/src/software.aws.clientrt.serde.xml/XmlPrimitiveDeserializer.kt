package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * Deserialize primitive values for single values, lists, and maps
 */
internal class XmlPrimitiveDeserializer(private val reader: XmlStreamReader, private val fieldDescriptor: SdkFieldDescriptor) :
    PrimitiveDeserializer {

    constructor(input: ByteArray, fieldDescriptor: SdkFieldDescriptor) : this(xmlStreamReader(input), fieldDescriptor)

    private suspend fun <T> deserializeValue(transform: ((String) -> T)): T {
        if (reader.peek() is XmlToken.BeginElement) {
            // In the case of flattened lists, we "fall" into the first node as there is no wrapper.
            // this conditional checks that case for the first element of the list.
            val wrapperToken = reader.takeNextAs<XmlToken.BeginElement>()
            if (wrapperToken.qualifiedName.name != fieldDescriptor.generalName()) {
                // Depending on flat/not-flat, may need to consume multiple start nodes
                return deserializeValue(transform)
            }
        }

        val token = reader.takeNextAs<XmlToken.Text>()

        val returnValue = token.value?.let { transform(it) }?.also {
            reader.takeNextAs<XmlToken.EndElement>()
        } ?: throw DeserializationException("Node specifies no or invalid value.")

        if (fieldDescriptor.hasTrait<XmlMapProperties>()) {
            // Optionally consume the entry wrapper
            val mapTrait = fieldDescriptor.findTrait() ?: XmlMapProperties.DEFAULT
            val nextToken = reader.peek()
            if (nextToken is XmlToken.EndElement) {
                val consumeEndToken = when (fieldDescriptor.hasTrait<Flattened>()) {
                    true -> nextToken.qualifiedName.name == fieldDescriptor.expectTrait<XmlSerialName>().name
                    false -> nextToken.qualifiedName.name == mapTrait.entry
                }
                if (consumeEndToken) reader.takeNextAs<XmlToken.EndElement>()
            }
        }

        return returnValue
    }

    override suspend fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte() ?: throw DeserializationException("Unable to deserialize $it as Byte") }

    override suspend fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it as Int") }

    override suspend fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort() ?: throw DeserializationException("Unable to deserialize $it as Short") }

    override suspend fun deserializeLong(): Long = deserializeValue { it.toLongOrNull() ?: throw DeserializationException("Unable to deserialize $it as Long") }

    override suspend fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull() ?: throw DeserializationException("Unable to deserialize $it as Float") }

    override suspend fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull() ?: throw DeserializationException("Unable to deserialize $it as Double") }

    override suspend fun deserializeString(): String = deserializeValue { it }

    override suspend fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override suspend fun deserializeNull(): Nothing? {
        reader.takeNextAs<XmlToken.EndElement>()
        return null
    }
}
