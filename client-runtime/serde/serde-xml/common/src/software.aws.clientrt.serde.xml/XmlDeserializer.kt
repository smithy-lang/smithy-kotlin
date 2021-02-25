/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

/**
 * Top-level class to drive deserialization messages into codegened response types.
 *
 * This deserializer only supports access methods which codegen produce.  For example,
 * a list cannot be deserialized without first deserializing a parent structure.
 */
// TODO - mark class internal and remove integration tests once serde is stable
class XmlDeserializer(private val reader: XmlStreamReader) : Deserializer {

    // Track each nested deserializer and the node level in which it was created.
    data class StructDeserializerInstance(
        val deserializer: XmlStructDeserializer,
        // Used to clean up deserializer state by removing instances of [StructDeserializerInstance]
        // that are deeper than the current node.
        val parseLevel: Int
    )

    private val structDeserializerStack = mutableListOf<StructDeserializerInstance>()

    constructor(input: ByteArray) : this(xmlStreamReader(input))

    override suspend fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
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
                structDeserializerStack.last().deserializer.clearParsedFields()

                // Optionally consume next token until we match our objectDescriptor.
                // This can vary depending on where deserializeStruct() is called from (list/map vs struct)
                var token = if (reader.currentToken is XmlToken.BeginElement) {
                    reader.currentToken as XmlToken.BeginElement
                } else {
                    reader.takeUntil()
                }

                val targetTokenName = descriptor.expectTrait<XmlSerialName>().name
                while (token.qualifiedName.name != targetTokenName) token =
                    reader.takeNextAs<XmlToken.BeginElement>()

                val structSerializer = XmlStructDeserializer(descriptor, reader)
                structDeserializerStack.add(StructDeserializerInstance(structSerializer, reader.currentDepth))
                structSerializer
            }
        }
    }

    override suspend fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        check(structDeserializerStack.isNotEmpty()) { "List cannot be deserialized independently from a parent struct" }
        cleanupDeserializerStack() // Set state on current deserializer that we are deserializing a container
        return XmlListDeserializer(
            descriptor,
            reader,
            structDeserializerStack.last().deserializer,
            primitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
        )
    }

    override suspend fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        check(structDeserializerStack.isNotEmpty()) { "Map cannot be deserialized independently from a parent struct" }
        cleanupDeserializerStack() // Set state on current deserializer that we are deserializing a container
        return XmlMapDeserializer(
            descriptor,
            reader,
            structDeserializerStack.last().deserializer,
            primitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
        )
    }

    // Each struct deserializer maintains a set of [FieldLocation]s.  When structs
    // traverse into another container, these field locations need to be cleared.  This is
    // because those are intended for use by [XmlStructDeserializer] and are not needed
    // for nested container types such as list and maps.  If they are not cleared when the
    // codegen'd deserializer code returns to the branching layer, it will return to codegen
    // that there are remaining fields to deserializer, but there are not.
    private fun cleanupDeserializerStack() {
        var deserializerInstance = structDeserializerStack.lastOrNull()

        while (deserializerInstance != null && deserializerInstance.parseLevel >= reader.currentDepth) {
            deserializerInstance.deserializer.clearParsedFields()
            structDeserializerStack.remove(deserializerInstance)
            deserializerInstance = structDeserializerStack.lastOrNull()
        }
        check(structDeserializerStack.isNotEmpty()) { "root deserializer should never be removed" }
    }
}

// Continue to consume tokens from the stream until the specified token is found.
internal suspend inline fun <reified TExpected : XmlToken> XmlStreamReader.takeUntil(): TExpected {
    var token = this.nextToken()
    while (token::class != TExpected::class && token !is XmlToken.EndDocument) {
        token = this.nextToken()
    }

    if (token::class != TExpected::class) throw DeserializerStateException("Expected ${TExpected::class} but instead found ${token::class}")
    return token as TExpected
}

// Return the next token of the specified type or throw [DeserializerStateException] if incorrect type.
internal suspend inline fun <reified TExpected : XmlToken> XmlStreamReader.takeNextAs(): TExpected {
    val token = this.nextToken()
    requireToken<TExpected>(token)
    return token as TExpected
}

// Reads the stream while until a node is not the specified type or the predicate returns true.
// Returns null if a different node was found or the node that matches the predicate.
internal suspend inline fun <reified TExpected : XmlToken> XmlStreamReader.takeUntil(predicate: (TExpected) -> Boolean = { true }): TExpected? {
    var token = nextToken()

    while (tokenIsType<TExpected>(token) && !predicate.invoke(token as TExpected)) {
        token = nextToken()
    }

    return if (tokenIsType<TExpected>(token)) token as TExpected else null
}

// require that the given token be of type [TExpected] or else throw an exception
internal inline fun <reified TExpected> tokenIsType(token: XmlToken) = token::class == TExpected::class

// require that the given token be of type [TExpected] or else throw an exception
internal inline fun <reified TExpected> requireToken(token: XmlToken) {
    if (token::class != TExpected::class) {
        throw DeserializerStateException("expected ${TExpected::class}; found ${token::class} ($token)")
    }
}
