$version: "2.0"

namespace aws.tests.serde.shared

list StringList {
    member: String,
}

@sparse
list SparseStringList {
    member: String
}

map StringMap {
    key: String,
    value: String,
}

map StringListMap {
    key: String,
    value: StringList
}

map NestedStringMap {
    key: String,
    value: StringMap
}

@sparse
map SparseStringMap {
    key: String,
    value: String,
}

list NestedStringList {
    member: StringList,
}

list IntegerList {
    member: Integer,
}

@uniqueItems
list IntegerSet {
    member: Integer,
}

enum FooEnum {
    FOO = "Foo"
    BAZ = "Baz"
    BAR = "Bar"
    ONE = "1"
    ZERO = "0"
}

list FooEnumList {
    member: FooEnum,
}

map FooEnumMap {
    key: String,
    value: FooEnum,
}

@timestampFormat("date-time")
timestamp DateTime

@timestampFormat("epoch-seconds")
timestamp EpochSeconds

@timestampFormat("http-date")
timestamp HttpDate

intEnum IntegerEnum {
    A = 1
    B = 2
    C = 3
}

list IntegerEnumList {
    member: IntegerEnum
}

map IntegerEnumMap {
    key: String,
    value: IntegerEnum
}


@mixin
structure PrimitiveTypesMixin {
    strField: String,
    byteField: Byte,
    intField: Integer,
    shortField: Short,
    longField: Long,
    floatField: Float,
    doubleField: Double,
    bigIntegerField: BigInteger,
    bigDecimalField: BigDecimal,
    boolField: Boolean,
    blobField: Blob,
    enumField: FooEnum,
    intEnumField: IntegerEnum,
    dateTimeField: DateTime,
    epochTimeField: EpochSeconds,
    httpTimeField: HttpDate,
}

@mixin
union PrimitiveTypesUnionMixin {
    strField: String,
    byteField: Byte,
    intField: Integer,
    shortField: Short,
    longField: Long,
    floatField: Float,
    doubleField: Double,
    bigIntegerField: BigInteger,
    bigDecimalField: BigDecimal,
    boolField: Boolean,
    blobField: Blob,
    enumField: FooEnum,
    intEnumField: IntegerEnum,
    dateTimeField: DateTime,
    epochTimeField: EpochSeconds,
    httpTimeField: HttpDate,
    unitField: Unit
}

@mixin
structure MapTypesMixin {
    normalMap: StringMap,
    sparseMap: SparseStringMap,
    nestedMap: NestedStringMap,
    listMap: StringListMap,
}

@mixin
union MapTypesUnionMixin {
    normalMap: StringMap,
    sparseMap: SparseStringMap,
    // FIXME - doesn't work with current codegen for unions
    // nestedMap: NestedStringMap,
}

@mixin
structure ListTypesMixin {
    normalList: StringList,
    sparseList: SparseStringList,
    nestedList: NestedStringList,
}

@mixin
union ListTypesUnionMixin {
    normalList: StringList,

    sparseList: SparseStringList,

    // FIXME - doesn't work with current codegen for unions
    // nestedList: NestedStringList,
}

