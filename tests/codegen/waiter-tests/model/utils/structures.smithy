$version: "2"
namespace com.test

use aws.protocols#restJson1
use smithy.waiters#waitable

@error("client")
structure NotFound { }

structure GetEntityRequest {
    @required
    @httpLabel
    name: String
}

structure GetEntityResponse {
    primitives: EntityPrimitives,
    lists: EntityLists,
    twoDimensionalLists: TwoDimensionalEntityLists,
    maps: EntityMaps,
    sampleValues: Values,
    objectOne: Values,
    objectTwo: Values,
}

structure EntityPrimitives {
    boolean: Boolean,
    string: String,
    byte: Byte,
    short: Short,
    integer: Integer,
    long: Long,
    float: Float,
    double: Double,
    enum: Enum,
    intEnum: IntEnum,

    // TODO: No defined comparison
    // blob: Blob,
    // bigInteger: BigInteger,
    // bigDecimal: BigDecimal,
    // timestamp: Timestamp,
}

structure EntityLists {
    booleans: BooleanList,
    strings: StringList,
    shorts: ShortList
    integers: IntegerList,
    longs: LongList
    floats: FloatList,
    doubles: DoubleList,
    enums: EnumList,
    intEnums: IntEnumList,
    structs: StructList,
}

structure TwoDimensionalEntityLists {
    booleansList: TwoDimensionalBooleanList,
}

structure EntityMaps {
    booleans: BooleanMap,
    strings: StringMap,
    integers: IntegerMap,
    enums: EnumMap,
    intEnums: IntEnumMap,
    structs: StructMap,
}

enum Enum {
    ONE = "one",
    TWO = "two",
}

intEnum IntEnum {
    ONE = 1,
    TWO = 2,
}

list BooleanList {
    member: Boolean,
}

list TwoDimensionalBooleanList {
    member: BooleanList,
}

list StringList {
    member: String,
}

list ShortList {
    member: Short,
}

list IntegerList {
    member: Integer,
}

list LongList {
    member: Long,
}

list FloatList {
    member: Float,
}

list DoubleList {
    member: Double,
}

list EnumList {
    member: Enum,
}

list IntEnumList {
    member: IntEnum,
}

list StructList {
    member: Struct,
}

map BooleanMap {
    key: String,
    value: Boolean,
}

map StringMap {
    key: String,
    value: String,
}

map IntegerMap {
    key: String,
    value: Integer,
}

map EnumMap {
    key: String,
    value: Enum,
}

map IntEnumMap {
    key: String,
    value: IntEnum,
}

map StructMap {
    key: String,
    value: Struct,
}

structure Struct {
    primitives: EntityPrimitives,
    strings: StringList,
    integer: Integer,
    string: String,
    enums: EnumList,
    subStructs: SubStructList,
}

list SubStructList {
    member: SubStruct,
}

structure SubStruct {
    subStructPrimitives: EntityPrimitives,
}

structure Values {
    valueOne: String
    valueTwo: String
    valueThree: String
}
