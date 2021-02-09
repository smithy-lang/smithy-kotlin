package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.FieldTrait
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.expectTrait

/**
 * Specifies a name that a field is encoded into for Json elements.
 */
data class JsonSerialName(val name: String) : FieldTrait

/**
 * Provides the serialized name of the field.
 */
val SdkFieldDescriptor.serialName: String
    get() = expectTrait<JsonSerialName>().name
