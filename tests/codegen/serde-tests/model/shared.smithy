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


union Choice {
    @xmlFlattened
    @xmlName("flatmap")
    flatMap: StringMap,

    normalMap: StringMap,

    sparseMap: SparseStringMap,

    // FIXME - doesn't work with current codegen
    // listMap: StringListMap,

    // FIXME - doesn't work with current codegen
    // nestedMap: NestedStringMap

    @xmlFlattened
    @xmlName("flatlist")
    flatList: StringList,

    normalList: StringList,

    sparseList: SparseStringList,

    // FIXME - doesn't work with current codegen
    // nestedList: NestedStringList,

    str: String,

    enum: FooEnum,

    dateTime: DateTime,
    epochTime: EpochSeconds,
    httpTime: HttpDate,

    @xmlName("double")
    fpDouble: Double,

    top: Top,

    blob: Blob,

    unit: Unit,

    // TODO - enum lists, timestamp lists, structure list, structure map, multiple flat lists interspersed (xml only)
}

structure Top {
    choice: Choice,

    strField: String,

    enumField: FooEnum,

    @xmlAttribute
    extra: Long,

    @xmlName("prefix:local")
    renamedWithPrefix: String,


    // FIXME - move back to Choice when supported properly
    listMap: StringListMap,
    nestedMap: NestedStringMap
    nestedList: NestedStringList,
}