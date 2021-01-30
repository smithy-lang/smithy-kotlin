package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.FieldTrait
import software.aws.clientrt.serde.SdkFieldDescriptor

data class JsonSerialName(val name: String) : FieldTrait

val SdkFieldDescriptor.serialName: String
    get() = expectTrait<JsonSerialName>().name
