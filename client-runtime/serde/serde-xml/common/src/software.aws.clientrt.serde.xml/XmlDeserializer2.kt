package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class XmlDeserializer2(private val reader: XmlStreamReader) : Deserializer {

    data class StructDeserializerInstance(val structSerializer: XmlStructDeserializer, val parseLevel: Int)

    private var structDeserializerStack = mutableListOf<StructDeserializerInstance>()

    constructor(input: ByteArray) : this(xmlStreamReader(input))

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        return when {
            structDeserializerStack.isEmpty() -> { // Root deserializer
                reader.takeUntil<XmlToken.BeginElement>()

                val structSerializer = XmlStructDeserializer(descriptor, reader)
                structDeserializerStack.add(StructDeserializerInstance(structSerializer, reader.currentDepth))
                structSerializer
            }
            else -> { // Nested deserializer
                // Flush existing token to avoid revisiting same node upon return
                // This is safe because attributes are always processed before children
                cleanupDeserializerStack()
                structDeserializerStack.last().structSerializer.clearNodeValueTokens()

                // Optionally consume next token until we match our objectDescriptor.
                // This can vary depending on where deserializeStruct() is called from (list/map vs struct)
                var token = if (reader.currentToken is XmlToken.BeginElement)
                    reader.currentToken as XmlToken.BeginElement
                else
                    reader.takeUntil()

                val targetTokenName = descriptor.expectTrait<XmlSerialName>().name
                while (token.qualifiedName.name != targetTokenName) token =
                    reader.takeNextTokenOf<XmlToken.BeginElement>()

                val structSerializer = XmlStructDeserializer(descriptor, reader)
                structDeserializerStack.add(StructDeserializerInstance(structSerializer, reader.currentDepth))
                structSerializer
            }
        }
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        check(structDeserializerStack.isNotEmpty()) { "List cannot be deserialized independently from a parent struct" }
        cleanupDeserializerStack()
        return XmlListDeserializer(
            descriptor,
            reader,
            structDeserializerStack.last().structSerializer,
            primitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
        )
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        check(structDeserializerStack.isNotEmpty()) { "Map cannot be deserialized independently from a parent struct" }
        cleanupDeserializerStack()
        return XmlMapDeserializer(
            descriptor,
            reader,
            structDeserializerStack.last().structSerializer,
            primitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
        )
    }

    // Each struct deserializer maintains a set of NodeValueTokens.  When structs
    // traverse into other container, these tokens need to be cleared.
    private fun cleanupDeserializerStack() {
        var pair = structDeserializerStack.lastOrNull()

        while (pair != null && pair.parseLevel >= reader.currentDepth) {
            pair.structSerializer.clearNodeValueTokens()
            structDeserializerStack.remove(pair)
            pair = structDeserializerStack.lastOrNull()
        }
        check(structDeserializerStack.isNotEmpty()) { "root deserializer should never be removed" }
    }
}

private inline fun <reified TExpected : XmlToken> XmlStreamReader.takeUntil(): TExpected {
    var token = this.takeNextToken()
    while (token::class != TExpected::class && token !is XmlToken.EndDocument) {
        token = this.takeNextToken()
    }

    if (token::class != TExpected::class) error("Did not find ${TExpected::class}")
    return token as TExpected
}
