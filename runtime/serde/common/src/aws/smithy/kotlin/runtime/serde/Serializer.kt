/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde
import aws.smithy.kotlin.runtime.smithy.Document
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.TimestampFormat

public interface Serializer : PrimitiveSerializer {
    /**
     * Begin a struct (i.e. in JSON this would be a '{') and return a [StructSerializer] that can be used to serialize the struct's fields.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    public fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer

    /**
     * Begin a list (i.e. in JSON this would be a '[') and return a [ListSerializer] that can be used to serialize the list's elements.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    public fun beginList(descriptor: SdkFieldDescriptor): ListSerializer

    /**
     * Begin a map (i.e. in JSON this would be a '{') and return a [MapSerializer] that can be used to serialize the map's entries.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    public fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer

    // FIXME - we should commonize how we deal with buffers internally and rely on `SdkBuffer`
    //  (likely once we roll our own json/xml serializers). Until then this should probably return a ByteStream?
    //  we could also supply an SdkBuffer.wrap(byteArray) function that sets the read/write to the limits of the array?
    /**
     * Consume the serializer and get the payload as a [ByteArray]
     */
    public fun toByteArray(): ByteArray
}

public interface StructSerializer : PrimitiveSerializer {

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Boolean)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Byte)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Short)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Char)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Int)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Long)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Float)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Double)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: String)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Instant, format: TimestampFormat)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: Document?)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    public fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the struct field using the given block.
     *
     * @param descriptor
     * @param block
     */
    public fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the list field using the given block.
     *
     * @param descriptor
     * @param block
     */
    public fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the map field using the given block.
     *
     * @param descriptor
     * @param block
     */
    public fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes null.
     */
    public fun nullField(descriptor: SdkFieldDescriptor)

    /**
     * Ends the struct that was started (i.e. in JSON this would be a '}').
     */
    public fun endStruct()
}

/**
 * Serializes a list.
 */
public interface ListSerializer : PrimitiveSerializer {

    /**
     * Ends the list that was started (i.e. in JSON this would be a ']').
     */
    public fun endList()
}

/**
 * Serializes a map. In Smithy, keys in maps are always Strings.
 */
public interface MapSerializer : PrimitiveSerializer {

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Boolean?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Byte?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Short?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Char?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Int?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Long?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Float?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Double?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: String?)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: Instant?, format: TimestampFormat)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    public fun entry(key: String, value: SdkSerializable?)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the list field using the given block.
     *
     * @param key
     * @param listDescriptor
     * @param block
     */
    public fun listEntry(key: String, listDescriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the map field using the given block.
     *
     * @param key
     * @param mapDescriptor
     * @param block
     */
    public fun mapEntry(key: String, mapDescriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit)

    /**
     * Ends the map that was started (i.e. in JSON this would be a '}').
     */
    public fun endMap()
}

/**
 * Used to serialize primitive values.
 */
public interface PrimitiveSerializer {

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeBoolean(value: Boolean)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeByte(value: Byte)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeShort(value: Short)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeChar(value: Char)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeInt(value: Int)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeLong(value: Long)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeFloat(value: Float)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeDouble(value: Double)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeString(value: String)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    public fun serializeInstant(value: Instant, format: TimestampFormat)

    /**
     * Calls the serialize method on the given object.
     *
     * @param value
     */
    public fun serializeSdkSerializable(value: SdkSerializable)

    /**
     * Serializes the given value.
     */
    public fun serializeNull()

    /**
     * Serializes the given value.
     */
    public fun serializeDocument(value: Document?)
}

/**
 * All components of a struct are expected to be serialized in the given block.
 */
public inline fun Serializer.serializeStruct(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: StructSerializer.() -> Unit) {
    val struct = beginStruct(sdkFieldDescriptor)
    struct.block()
    struct.endStruct()
}

/**
 * All elements of a list are expected to be serialized in the given block.
 */
public inline fun Serializer.serializeList(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: ListSerializer.() -> Unit) {
    val list = beginList(sdkFieldDescriptor)
    list.block()
    list.endList()
}

/**
 * All entries of a map are expected to be serialized in the given block.
 */
public inline fun Serializer.serializeMap(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: MapSerializer.() -> Unit) {
    val map = beginMap(sdkFieldDescriptor)
    map.block()
    map.endMap()
}
