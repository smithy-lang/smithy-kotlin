$version: "2.0"
namespace smithy.kotlin.nullability

use aws.protocols#awsJson1_0

@awsJson1_0
service NullCheckService {
    operations: [SayHello],
    version: "1"
}

operation SayHello {
    input: TestInput
}

@input
structure TestInput {
    nested: TestStruct,

    @default("hammer")
    tay: String

    @required
    @default("ball")
    lep: String
}

structure TestStruct {
    @required
    strValue: String,

    @required
    byteValue: Byte,

    @required
    listValue: StringList,

    @required
    mapValue: ListMap,

    @required
    nestedListValue: NestedList

    @required
    document: Document

    @required
    nested: Nested

    @required
    blob: Blob

    @required
    enum: Enum

    @required
    intEnum: IntEnum

    @required
    union: U

    notRequired: String,

    @default(0)
    defaultIntValue: Integer,

    @required
    @clientOptional
    clientOptionalValue: Integer

    @required
    @default("model default")
    @clientOptional
    clientOptionalWithDefault: String
}

enum Enum {
    A,
    B,
    C
}

intEnum IntEnum {
    ONE =   1,
    TWO =   2,
    THREE = 3
}

union U {
    A: Integer,
    B: String,
    C: Unit
}

structure Nested {
    @required
    a: String
}

list StringList {
    member: String
}

list NestedList {
    member: StringList
}

map ListMap {
    key: String,
    value: StringList
}