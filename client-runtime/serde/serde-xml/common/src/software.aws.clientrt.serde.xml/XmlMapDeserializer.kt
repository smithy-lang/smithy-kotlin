package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlMapDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.EntryIterator, PrimitiveDeserializer by primitiveDeserializer {

    override suspend fun hasNextEntry(): Boolean = when (reader.peekNextToken()) {
        is XmlToken.EndDocument -> false
        is XmlToken.EndElement -> {
            parentDeserializer.clearNodeValueTokens()

            reader.takeNextAs<XmlToken.EndElement>()
            if (fieldDescriptor.findTrait<XmlMap>()?.flattened == false && reader.peekNextToken() is XmlToken.EndElement) {
                reader.takeNextAs<XmlToken.EndElement>()
            }

            reader.peekNextToken() is XmlToken.BeginElement
        }
        else -> true
    }

    override suspend fun key(): String {
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()
        reader.takeUntil<XmlToken.BeginElement> { it.qualifiedName.name == mapTrait.keyName } ?: throw DeserializerStateException("Expected node named ${mapTrait.keyName}")
        val keyValue = reader.takeNextAs<XmlToken.Text>()

        if (keyValue.value == null || keyValue.value.isBlank()) throw DeserializerStateException("Key entry is empty.")
        if (reader.takeNextAs<XmlToken.EndElement>().qualifiedName.name != mapTrait.keyName) throw DeserializerStateException("Expected end of key field")

        return keyValue.value
    }

    override suspend fun nextHasValue(): Boolean {
        val valueWrapperToken = reader.takeNextAs<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()

        if (valueWrapperToken.qualifiedName.name != mapTrait.valueName) throw DeserializerStateException("Expected map value name but found ${valueWrapperToken.qualifiedName}")

        val nextToken = reader.peekNextToken()

        return nextToken is XmlToken.Text || nextToken is XmlToken.BeginElement
    }
}
