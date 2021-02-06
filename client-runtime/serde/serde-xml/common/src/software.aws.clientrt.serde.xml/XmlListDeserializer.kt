package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.PrimitiveDeserializer
import software.aws.clientrt.serde.SdkFieldDescriptor

class XmlListDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.ElementIterator, PrimitiveDeserializer by primitiveDeserializer {

    override suspend fun hasNextElement(): Boolean = when (reader.peekNextToken()) {
        is XmlToken.EndDocument -> false
        is XmlToken.EndElement -> {
            parentDeserializer.clearNodeValueTokens()

            if (fieldDescriptor.findTrait<XmlList>()?.flattened == false && reader.peekNextToken() is XmlToken.EndElement) {
                reader.takeNextOf<XmlToken.EndElement>()
            }

            reader.peekNextToken() is XmlToken.BeginElement
        }
        else -> true
    }

    override suspend fun nextHasValue(): Boolean {
        return when (reader.peekNextToken()) {
            is XmlToken.EndElement,
            is XmlToken.EndDocument -> false
            is XmlToken.BeginElement -> {
                // Here we need to read the next token so we can peek the next to determine if there is a value.
                // deserializeValue() can conditionally handle start or value nodes
                reader.takeNextOf<XmlToken.BeginElement>()

                when (reader.peekNextToken()) {
                    is XmlToken.EndElement,
                    is XmlToken.EndDocument -> false
                    else -> true
                }
            }
            else -> true
        }
    }
}
