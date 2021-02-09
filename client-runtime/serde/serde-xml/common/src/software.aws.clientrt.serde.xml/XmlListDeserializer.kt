package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

internal class XmlListDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.ElementIterator, PrimitiveDeserializer by primitiveDeserializer {

    override suspend fun hasNextElement(): Boolean = when (reader.peek()) {
        is XmlToken.EndDocument -> throw DeserializerStateException("Unexpected end of document.")
        is XmlToken.EndElement -> {
            if (!fieldDescriptor.hasTrait<Flattened>() && reader.peek() is XmlToken.EndElement) {
                reader.takeNextAs<XmlToken.EndElement>()
            }

            val hasNext = reader.peek() is XmlToken.BeginElement
            if (!hasNext) parentDeserializer.clearParsedFields()
            hasNext
        }
        else -> true
    }

    override suspend fun nextHasValue(): Boolean {
        return when (reader.peek()) {
            is XmlToken.EndElement,
            is XmlToken.EndDocument -> false
            is XmlToken.BeginElement -> {
                // Here we need to read the next token so we can peek the next to determine if there is a value.
                // deserializeValue() can conditionally handle start or value nodes
                reader.takeNextAs<XmlToken.BeginElement>()

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
