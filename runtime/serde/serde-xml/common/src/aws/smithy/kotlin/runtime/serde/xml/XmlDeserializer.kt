/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.serde.*

// Represents aspects of SdkFieldDescriptor that are particular to the Xml format
internal sealed class FieldLocation {
    // specifies the mapping to a sdk field index
    abstract val fieldIndex: Int

    data class Text(override val fieldIndex: Int) : FieldLocation() // Xml nodes have only one associated Text element
    data class Attribute(override val fieldIndex: Int, val names: Set<XmlToken.QualifiedName>) : FieldLocation()
}

/**
 * Provides a deserializer for XML documents
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param validateRootElement Flag indicating if the root XML document [XmlToken.BeginElement] should be validated against
 * the descriptor passed to [deserializeStruct]. This only affects the root element, not nested struct elements. Some
 * restXml based services DO NOT always send documents with a root element name that matches the shape ID name
 * (S3 in particular). This means there is nothing in the model that gives you enough information to validate the tag.
 */
class XmlDeserializer(
    private val reader: XmlStreamReader,
    private val validateRootElement: Boolean = false
) : Deserializer {

    constructor(input: ByteArray, validateRootElement: Boolean = false) : this(xmlStreamReader(input), validateRootElement)

    private val logger = Logger.getLogger<XmlDeserializer>()
    private var firstStructCall = true

    override fun deserializeStruct(descriptor: SdkObjectDescriptor): Deserializer.FieldIterator {
        logger.trace { "Deserializing struct $descriptor under ${reader.lastToken}" }

        if (firstStructCall) {
            if (!descriptor.hasTrait<XmlSerialName>()) throw DeserializationException("Top-level struct $descriptor requires a XmlSerialName trait but has none.")

            firstStructCall = false

            reader.nextToken() // Matching field descriptors to children tags so consume the start element of top-level struct

            val structToken = if (descriptor.hasTrait<XmlError>()) {
                reader.seek<XmlToken.BeginElement> { it.name == descriptor.expectTrait<XmlError>().errorTag }
            } else {
                reader.seek<XmlToken.BeginElement>()
            } ?: throw DeserializationException("Could not find a begin element for new struct")

            if (validateRootElement) {
                descriptor.requireNameMatch(structToken.name.tag)
            }
        }

        // Consume any remaining terminating tokens from previous deserialization
        reader.seek<XmlToken.BeginElement>()

        // Because attributes set on the root node of the struct, we must read the values before creating the subtree
        val attribFields = reader.tokenAttributesToFieldLocations(descriptor)
        val parentToken = if (reader.lastToken is XmlToken.BeginElement) {
            reader.lastToken as XmlToken.BeginElement
        } else {
            throw DeserializationException("Expected last parsed token to be ${XmlToken.BeginElement::class} but was ${reader.lastToken}")
        }

        return XmlStructDeserializer(descriptor, reader.subTreeReader(), parentToken, attribFields)
    }

    override fun deserializeList(descriptor: SdkFieldDescriptor): Deserializer.ElementIterator {
        logger.trace { "Deserializing list $descriptor under ${reader.lastToken}" }

        val depth = when (descriptor.hasTrait<Flattened>()) {
            true -> XmlStreamReader.SubtreeStartDepth.CURRENT
            else -> XmlStreamReader.SubtreeStartDepth.CHILD
        }

        return XmlListDeserializer(reader.subTreeReader(depth), descriptor)
    }

    override fun deserializeMap(descriptor: SdkFieldDescriptor): Deserializer.EntryIterator {
        logger.trace { "Deserializing map $descriptor under ${reader.lastToken}" }

        return XmlMapDeserializer(reader.subTreeReader(XmlStreamReader.SubtreeStartDepth.CURRENT), descriptor)
    }
}

/**
 * Deserializes specific XML structures into forms that can produce Maps
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param descriptor associated [SdkFieldDescriptor] which represents the expected Map
 * @param primitiveDeserializer used to deserialize primitive values
 */
internal class XmlMapDeserializer(
    private val reader: XmlStreamReader,
    private val descriptor: SdkFieldDescriptor,
    private val primitiveDeserializer: PrimitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
) : PrimitiveDeserializer by primitiveDeserializer, Deserializer.EntryIterator {
    private val mapTrait = descriptor.findTrait<XmlMapName>() ?: XmlMapName.Default

    override fun hasNextEntry(): Boolean {
        // Seek to either the entry or key token depending on the flatness of the map
        val nextEntryToken = when (descriptor.hasTrait<Flattened>()) {
            true -> reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.key }
            false -> reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.entry }
        }

        return nextEntryToken != null
    }

    override fun key(): String {
        // Seek to the key begin token
        reader.seek<XmlToken.BeginElement> { it.name.local == mapTrait.key }
            ?: error("Unable to find key $mapTrait.key in $descriptor")

        val keyValueToken = reader.takeNextAs<XmlToken.Text>()
        reader.nextToken() // Consume the end wrapper

        return keyValueToken.value ?: throw DeserializationException("Key unspecified in $descriptor")
    }

    override fun nextHasValue(): Boolean {
        // Expect a begin and value (or another begin) token if Map entry has a value
        val peekBeginToken = reader.peek(1) ?: throw DeserializationException("Unexpected termination of token stream in $descriptor")
        val peekValueToken = reader.peek(2) ?: throw DeserializationException("Unexpected termination of token stream in $descriptor")

        return peekBeginToken !is XmlToken.EndElement && peekValueToken !is XmlToken.EndElement
    }
}

/**
 * Deserializes specific XML structures into forms that can produce Lists
 *
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param descriptor associated [SdkFieldDescriptor] which represents the expected Map
 * @param primitiveDeserializer used to deserialize primitive values
 */
internal class XmlListDeserializer(
    private val reader: XmlStreamReader,
    private val descriptor: SdkFieldDescriptor,
    private val primitiveDeserializer: PrimitiveDeserializer = XmlPrimitiveDeserializer(reader, descriptor)
) : PrimitiveDeserializer by primitiveDeserializer, Deserializer.ElementIterator {
    private var firstCall = true
    private val flattened = descriptor.hasTrait<Flattened>()
    private val elementName = (descriptor.findTrait<XmlCollectionName>() ?: XmlCollectionName.Default).element

    override fun hasNextElement(): Boolean {
        if (!flattened && firstCall) {
            val nextToken = reader.peek()
            val matchedListDescriptor = nextToken is XmlToken.BeginElement && descriptor.nameMatches(nextToken.name.tag)
            val hasChildren = if (nextToken == null) false else nextToken.depth >= reader.lastToken!!.depth

            if (!matchedListDescriptor && !hasChildren) return false

            // Discard the wrapper and move to the first element in the list
            if (matchedListDescriptor) reader.nextToken()

            firstCall = false
        }

        if (flattened) {
            // Because our subtree is not CHILD, we cannot rely on the subtree boundary to determine end of collection.
            // Rather, we search for either the next begin token matching the (flat) list member name which should
            // be immediately after the current token

            // peek at the next token if there is one, in the case of a list of structs, the next token is actually
            // the end of the current flat list element in which case we need to peek twice
            val next = when (val peeked = reader.peek()) {
                is XmlToken.EndElement -> {
                    if (peeked.name.local == descriptor.serialName.name) {
                        // consume the end token
                        reader.nextToken()
                        reader.peek()
                    } else {
                        peeked
                    }
                }
                else -> peeked
            }

            val tokens = listOfNotNull(reader.lastToken, next)

            // Iterate over the token stream until begin token matching name is found or end element matching list is found.
            return tokens
                .filterIsInstance<XmlToken.BeginElement>()
                .any { it.name.local == descriptor.serialName.name }
        } else {
            // If we can find another begin token w/ the element name, we have more elements to process
            return reader.seek<XmlToken.BeginElement> { it.name.local == elementName }.isNotTerminal()
        }
    }

    override fun nextHasValue(): Boolean = reader.peek() !is XmlToken.EndElement
}

/**
 * Deserializes specific XML structures into forms that can produce structures
 *
 * @param objDescriptor associated [SdkObjectDescriptor] which represents the expected structure
 * @param reader underlying [XmlStreamReader] from which tokens are read
 * @param parentToken initial token of associated structure
 * @param parsedFieldLocations list of [FieldLocation] representing values able to be loaded into deserialized instances
 */
internal class XmlStructDeserializer(
    private val objDescriptor: SdkObjectDescriptor,
    private val reader: XmlStreamReader,
    private val parentToken: XmlToken.BeginElement,
    private val parsedFieldLocations: MutableList<FieldLocation> = mutableListOf()
) : Deserializer.FieldIterator {
    // Used to track direct deserialization or further nesting between calls to findNextFieldIndex() and deserialize<Type>()
    private var reentryFlag: Boolean = false

    override fun findNextFieldIndex(): Int? {
        if (inNestedMode()) {
            // Returning from a nested struct call.  Nested deserializer consumed
            // tokens so clear them here to avoid processing stale state
            parsedFieldLocations.clear()
        }

        if (parsedFieldLocations.isEmpty()) {
            val matchedFieldLocations = when (val token = reader.nextToken()) {
                null, is XmlToken.EndDocument -> return null
                is XmlToken.EndElement -> return findNextFieldIndex()
                is XmlToken.BeginElement -> {
                    val nextToken = reader.peek() ?: return null
                    val objectFields = objDescriptor.fields
                    val memberFields = objectFields.filter { field -> objDescriptor.fieldTokenMatcher(field, token) }
                    val matchingFields = memberFields.mapNotNull { it.findFieldLocation(token, nextToken) }
                    matchingFields
                }
                else -> return findNextFieldIndex()
            }

            // Sorting ensures attribs are processed before text, as processing the Text token pushes the parser on to the next token.
            parsedFieldLocations.addAll(matchedFieldLocations.sortedBy { it is FieldLocation.Text })
        }

        return parsedFieldLocations.firstOrNull()?.fieldIndex ?: Deserializer.FieldIterator.UNKNOWN_FIELD
    }

    private fun <T> deserializeValue(transform: ((String) -> T)): T {
        // Set and validate mode
        reentryFlag = false
        if (parsedFieldLocations.isEmpty()) throw DeserializationException("matchedFields is empty, was findNextFieldIndex() called?")

        // Take the first FieldLocation and attempt to parse it into the value specified by the descriptor.
        return when (val nextField = parsedFieldLocations.removeFirst()) {
            is FieldLocation.Text -> {
                val value = when (val peekToken = reader.peek()) {
                    is XmlToken.Text -> reader.takeNextAs<XmlToken.Text>().value ?: ""
                    is XmlToken.EndElement -> ""
                    else -> throw DeserializationException("Unexpected token $peekToken")
                }
                transform(value)
            }
            is FieldLocation.Attribute -> {
                transform(
                    nextField
                        .names
                        .mapNotNull { parentToken.attributes[it] }
                        .firstOrNull() ?: throw DeserializationException("Expected attrib value ${nextField.names.first()} not found in ${parentToken.name}")
                )
            }
        }
    }

    override fun skipValue() = reader.skipNext()

    override fun deserializeByte(): Byte = deserializeValue { it.toIntOrNull()?.toByte() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeInt(): Int = deserializeValue { it.toIntOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeShort(): Short = deserializeValue { it.toIntOrNull()?.toShort() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeLong(): Long = deserializeValue { it.toLongOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeFloat(): Float = deserializeValue { it.toFloatOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeDouble(): Double = deserializeValue { it.toDoubleOrNull() ?: throw DeserializationException("Unable to deserialize $it") }

    override fun deserializeString(): String = deserializeValue { it }

    override fun deserializeBoolean(): Boolean = deserializeValue { it.toBoolean() }

    override fun deserializeNull(): Nothing? {
        reader.takeNextAs<XmlToken.EndElement>()
        return null
    }

    // A struct deserializer can be called in two "modes":
    // 1. to deserialize a value.  This calls findNextFieldIndex() followed by deserialize<SomePrimitiveType>()
    // 2. to deserialize a nested container. This calls findNextFieldIndex() followed by a call to another deserialize<Struct/List/Map>()
    // Because state is built in findNextFieldIndex() that is intended to be used directly in deserialize<SomePrimitiveType>() (mode 1)
    // and there is no explicit way that this type knows which mode is in use, the state built must be cleared.
    // this is done by flipping a bit between the two calls.  If the bit has not been flipped on any call to findNextFieldIndex()
    // it is determined that the nested mode was used and any existing state should be cleared.
    // if the state is not cleared, deserialization goes into an infinite loop because the deserializer sees pending fields to pull from the stream
    // which are never consumed by the (missing) call to deserialize<SomePrimitiveType>()
    private fun inNestedMode(): Boolean = when (reentryFlag) {
        true -> true
        false -> { reentryFlag = true; false }
    }
}

// Extract the attributes from the last-read token and match them to [FieldLocation] on the [SdkObjectDescriptor].
private fun XmlStreamReader.tokenAttributesToFieldLocations(descriptor: SdkObjectDescriptor): MutableList<FieldLocation> =
    if (descriptor.hasXmlAttributes && lastToken is XmlToken.BeginElement) {
        val attribFields = descriptor.fields.filter { it.hasTrait<XmlAttribute>() }
        val matchedAttribFields = attribFields.filter { it.findFieldLocation(lastToken as XmlToken.BeginElement, peek() ?: throw DeserializationException("Unexpected end of tokens")) != null }
        matchedAttribFields.map { FieldLocation.Attribute(it.index, it.toQualifiedNames()) }
            .toMutableList()
    } else {
        mutableListOf()
    }

// Returns a [FieldLocation] if the field maps to the current token
private fun SdkFieldDescriptor.findFieldLocation(
    currentToken: XmlToken.BeginElement,
    nextToken: XmlToken,
): FieldLocation? = when (val property = toFieldLocation()) {
    is FieldLocation.Text -> {
        when {
            nextToken is XmlToken.Text -> property
            nextToken is XmlToken.BeginElement -> property
            // The following allows for struct primitives to remain unvisited if no value
            // but causes nested deserializers to be called even if they contain no value
            nextToken is XmlToken.EndElement && currentToken.name == nextToken.name -> property
            else -> null
        }
    }
    is FieldLocation.Attribute -> {
        val foundMatch = property.names.any { currentToken.attributes[it]?.isNotBlank() == true }
        if (foundMatch) property else null
    }
}

// Produce a [FieldLocation] type based on presence of traits of field
// A field without an attribute trait is assumed to be a text token
private fun SdkFieldDescriptor.toFieldLocation(): FieldLocation =
    when (findTrait<XmlAttribute>()) {
        null -> FieldLocation.Text(index) // Assume a text value if no attributes defined.
        else -> FieldLocation.Attribute(index, toQualifiedNames())
    }

// Matches fields and tokens with matching qualified name
private fun SdkObjectDescriptor.fieldTokenMatcher(fieldDescriptor: SdkFieldDescriptor, beginElement: XmlToken.BeginElement): Boolean {
    if (fieldDescriptor.kind == SerialKind.List && fieldDescriptor.hasTrait<Flattened>()) {
        val fieldName = fieldDescriptor.findTrait<XmlCollectionName>() ?: XmlCollectionName.Default
        val tokenQname = beginElement.name

        // It may be that we are matching a flattened list element or matching a list itself.  In the latter
        // case the following predicate will not work, so if we fail to match the member
        // try again (below) to match against the container.
        if (fieldName.element == tokenQname.local) return true
    }

    return fieldDescriptor.nameMatches(beginElement.name.tag)
}

// Return the next token of the specified type or throw [DeserializerStateException] if incorrect type.
internal inline fun <reified TExpected : XmlToken> XmlStreamReader.takeNextAs(): TExpected {
    val token = this.nextToken() ?: throw DeserializationException("Expected ${TExpected::class} but instead found null")
    requireToken<TExpected>(token)
    return token as TExpected
}

// require that the given token be of type [TExpected] or else throw an exception
internal inline fun <reified TExpected> requireToken(token: XmlToken) {
    if (token::class != TExpected::class) {
        throw DeserializationException("expected ${TExpected::class}; found ${token::class} ($token)")
    }
}
