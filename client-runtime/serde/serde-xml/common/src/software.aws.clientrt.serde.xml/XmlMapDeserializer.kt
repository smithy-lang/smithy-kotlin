package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.PrimitiveDeserializer
import software.aws.clientrt.serde.SdkFieldDescriptor

class XmlMapDeserializer(
    private val fieldDescriptor: SdkFieldDescriptor,
    private val reader: XmlStreamReader,
    private val parentDeserializer: XmlStructDeserializer,
    private val startLevel: Int = reader.currentDepth,
    primitiveDeserializer: XmlPrimitiveDeserializer
) : Deserializer.EntryIterator, PrimitiveDeserializer by primitiveDeserializer {

    override fun hasNextEntry(): Boolean = when (reader.peekNextToken()) {
        is XmlToken.EndDocument -> false
        is XmlToken.EndElement -> {
            parentDeserializer.clearNodeValueTokens()
            //Depending on flat/not-flat, may need to pull of multiple end nodes
            while (reader.currentDepth >= startLevel && reader.peekNextToken() is XmlToken.EndElement) reader.takeNextTokenOf<XmlToken.EndElement>()
            reader.peekNextToken() is XmlToken.BeginElement
        }
        else -> true
    }

    override fun key(): String {
        var token = reader.takeNextTokenOf<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()

        return if (mapTrait.flattened) {
            if (token.qualifiedName.name == fieldDescriptor.expectTrait<XmlSerialName>().name) {
                token = reader.takeNextTokenOf<XmlToken.BeginElement>()
            }

            check(token.qualifiedName.name == mapTrait.keyName) { "Expected ${mapTrait.keyName} representing key of map, but found ${token.qualifiedName}" }
            val keyValue = reader.takeNextTokenOf<XmlToken.Text>()
            check(keyValue.value != null && keyValue.value.isNotBlank()) { "Key entry is empty." }
            check(reader.takeNextTokenOf<XmlToken.EndElement>().qualifiedName.name == "key") { "Expected end of key field" }
            keyValue.value
        } else {
            check(token.qualifiedName.name == mapTrait.entry) { "Expected token representing entry of map, but found ${token.qualifiedName}" }
            val keyToken = reader.takeNextTokenOf<XmlToken.BeginElement>()
            // See https://awslabs.github.io/smithy/1.0/spec/core/model.html#map
            check(keyToken.qualifiedName.name == mapTrait.keyName) { "Expected key field, but found ${keyToken.qualifiedName}" }
            val keyValue = reader.takeNextTokenOf<XmlToken.Text>()
            check(keyValue.value != null && keyValue.value.isNotBlank()) { "Key entry is empty." }
            check(reader.takeNextTokenOf<XmlToken.EndElement>().qualifiedName.name == mapTrait.keyName) { "Expected end of key field" }
            keyValue.value
        }
    }

    override fun nextHasValue(): Boolean {
        val valueWrapperToken = reader.takeNextTokenOf<XmlToken.BeginElement>()
        val mapTrait = fieldDescriptor.expectTrait<XmlMap>()
        check(valueWrapperToken.qualifiedName.name == mapTrait.valueName) { "Expected map value name but found ${valueWrapperToken.qualifiedName}" }

        val nextToken = reader.peekNextToken()

        return nextToken is XmlToken.Text || nextToken is XmlToken.BeginElement
    }
}