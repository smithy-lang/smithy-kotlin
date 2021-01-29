package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.FieldTrait
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SerialKind

data class SerialName(val name: String) : FieldTrait

val SdkFieldDescriptor.serialName: String
    get() = expectTrait<SerialName>().name

fun SdkFieldDescriptor.Companion.fromSerialName(name: String, kind: SerialKind): SdkFieldDescriptor = SdkFieldDescriptor(kind = kind, trait = SerialName(name))

object AnonymousStructFieldDescriptor: SdkFieldDescriptor(kind = SerialKind.Struct)