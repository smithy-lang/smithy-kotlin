/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde

interface Serializer : PrimitiveSerializer {
    // TODO: time related structs, bigInteger, bigDecimal, set. bigInteger/bigDecimal will have to be JVM specific.

    /**
     * Begin a struct (i.e. in JSON this would be a '{') and return a [StructSerializer] that can be used to serialize the struct's fields.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    fun beginStruct(descriptor: SdkFieldDescriptor): StructSerializer

    /**
     * Begin a list (i.e. in JSON this would be a '[') and return a [ListSerializer] that can be used to serialize the list's elements.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    fun beginList(descriptor: SdkFieldDescriptor): ListSerializer

    /**
     * Begin a map (i.e. in JSON this would be a '{') and return a [MapSerializer] that can be used to serialize the map's entries.
     *
     * @param descriptor SdkFieldDescriptor of container for formats that require them.
     */
    fun beginMap(descriptor: SdkFieldDescriptor): MapSerializer

    /**
     * Consume the serializer and get the payload as a [ByteArray]
     */
    fun toByteArray(): ByteArray
}

interface StructSerializer : PrimitiveSerializer {

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Boolean)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Byte)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Short)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Char)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Int)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Long)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Float)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: Double)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: String)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes value.
     *
     * @param descriptor
     * @param value
     */
    fun field(descriptor: SdkFieldDescriptor, value: SdkSerializable)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the struct field using the given block.
     *
     * @param descriptor
     * @param block
     */
    fun structField(descriptor: SdkFieldDescriptor, block: StructSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the list field using the given block.
     *
     * @param descriptor
     * @param block
     */
    fun listField(descriptor: SdkFieldDescriptor, block: ListSerializer.() -> Unit)

    /**
     * Writes the field name given in the descriptor, and then
     * serializes the map field using the given block.
     *
     * @param descriptor
     * @param block
     */
    fun mapField(descriptor: SdkFieldDescriptor, block: MapSerializer.() -> Unit)

    /**
     * Ends the struct that was started (i.e. in JSON this would be a '}').
     */
    fun endStruct()
}

/**
 * Serializes a list.
 */
interface ListSerializer : PrimitiveSerializer {

    /**
     * Ends the list that was started (i.e. in JSON this would be a ']').
     */
    fun endList()
}

/**
 * Serializes a map. In Smithy, keys in maps are always Strings.
 */
interface MapSerializer : PrimitiveSerializer {

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Boolean)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Byte)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Short)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Char)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Int)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Long)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Float)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: Double)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: String)

    /**
     * Writes the key given in the descriptor, and then
     * serializes value.
     *
     * @param key
     * @param value
     */
    fun entry(key: String, value: SdkSerializable)

    /**
     * Ends the map that was started (i.e. in JSON this would be a '}').
     */
    fun endMap()
}

/**
 * Used to serialize primitive values.
 */
interface PrimitiveSerializer {

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeBoolean(value: Boolean)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeByte(value: Byte)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeShort(value: Short)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeChar(value: Char)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeInt(value: Int)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeLong(value: Long)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeFloat(value: Float)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeDouble(value: Double)

    /**
     * Serializes the given value.
     *
     * @param value
     */
    fun serializeString(value: String)

    /**
     * Calls the serialize method on the given object.
     *
     * @param value
     */
    fun serializeSdkSerializable(value: SdkSerializable)

    /**
     * Serializes the given value.
     *
     * @param descriptor
     */
    fun serializeNull(descriptor: SdkFieldDescriptor)
}

/**
 * All components of a struct are expected to be serialized in the given block.
 */
inline fun Serializer.serializeStruct(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: StructSerializer.() -> Unit) {
    val struct = beginStruct(sdkFieldDescriptor)
    struct.block()
    struct.endStruct()
}

/**
 * All elements of a list are expected to be serialized in the given block.
 */
inline fun Serializer.serializeList(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: ListSerializer.() -> Unit) {
    val list = beginList(sdkFieldDescriptor)
    list.block()
    list.endList()
}

/**
 * All entries of a map are expected to be serialized in the given block.
 */
inline fun Serializer.serializeMap(sdkFieldDescriptor: SdkFieldDescriptor, crossinline block: MapSerializer.() -> Unit) {
    val map = beginMap(sdkFieldDescriptor)
    map.block()
    map.endMap()
}
