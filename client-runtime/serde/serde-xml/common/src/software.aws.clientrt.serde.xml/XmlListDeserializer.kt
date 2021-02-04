package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.PrimitiveDeserializer

class XmlListDeserializer(
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    private val startLevel: Int = reader.currentDepth,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.ElementIterator, PrimitiveDeserializer by primitiveDeserializer {

    override fun hasNextElement(): Boolean = when (reader.peekNextToken()) {
        is XmlToken.EndDocument -> false
        is XmlToken.EndElement -> {
            parentDeserializer.clearNodeValueTokens()
            //Depending on flat/not-flat, may need to pull of multiple end nodes
            while (reader.currentDepth >= startLevel && reader.peekNextToken() is XmlToken.EndElement) reader.takeNextTokenOf<XmlToken.EndElement>()
            false
        }
        else -> true
    }

    override fun nextHasValue(): Boolean {
        return when (reader.peekNextToken()) {
            is XmlToken.EndElement,
            is XmlToken.EndDocument -> false
            is XmlToken.BeginElement -> {
                // Here we need to read the next token so we can peek the next to determine if there is a value.
                // deserializeValue() can conditionally handle start or value nodes
                reader.takeNextTokenOf<XmlToken.BeginElement>()

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