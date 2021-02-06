package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.DeserializerStateException
import software.aws.clientrt.serde.PrimitiveDeserializer
import software.aws.clientrt.serde.SdkFieldDescriptor

class XmlMapDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.EntryIterator, PrimitiveDeserializer by primitiveDeserializer {

    override fun hasNextEntry(): Boolean = when (reader.peekNextToken()) {
        is XmlToken.EndDocument -> false
        is XmlToken.EndElement -> {
            parentDeserializer.clearNodeValueTokens()

            reader.takeNextTokenOf<XmlToken.EndElement>()
            if (fieldDescriptor.findTrait<XmlMap>()?.flattened == false && reader.peekNextToken() is XmlToken.EndElement) {
                reader.takeNextTokenOf<XmlToken.EndElement>()
            }

            reader.peekNextToken() is XmlToken.BeginElement
        }
        else -> true
    }

    override fun key(): String {
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()
        reader.takeAllUntil<XmlToken.BeginElement> { it.qualifiedName.name == mapTrait.keyName } ?: error("wtf")
        val keyValue = reader.takeNextTokenOf<XmlToken.Text>()

        if (keyValue.value == null || keyValue.value.isBlank()) throw DeserializerStateException("Key entry is empty.")
        if (reader.takeNextTokenOf<XmlToken.EndElement>().qualifiedName.name != mapTrait.keyName) throw DeserializerStateException("Expected end of key field")

        return keyValue.value
    }

    override fun nextHasValue(): Boolean {
        val valueWrapperToken = reader.takeNextTokenOf<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()

        if (valueWrapperToken.qualifiedName.name != mapTrait.valueName) throw DeserializerStateException("Expected map value name but found ${valueWrapperToken.qualifiedName}")

        val nextToken = reader.peekNextToken()

        return nextToken is XmlToken.Text || nextToken is XmlToken.BeginElement
    }
}
