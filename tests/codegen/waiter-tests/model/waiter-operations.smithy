$version: "2"
namespace com.test

use aws.protocols#restJson1
use smithy.waiters#waitable

service WaitersTestService {
    operations: [GetEntity]
}

@waitable(
    // primitive equality
    BooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.boolean == `true`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    StringEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.string == `\"foo\"`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ByteEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.byte == `1`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.short == `2`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.integer == `3`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    LongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.long == `4`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    FloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.float == `5.0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    DoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.double == `6.0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.enum",
                        expected: "one",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    EnumEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.enum == `\"one\"`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.intEnum == `1`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // comparators
    AndEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0] && lists.booleans[1]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    OrEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0] || lists.booleans[1]",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    NotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "!(primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // list indexing
    BooleanListIndexZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListIndexOneEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[1]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListIndexNegativeTwoEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[-2]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    TwoDimensionalBooleanListIndexZeroZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "twoDimensionalLists.booleansList[0][0]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StructListIndexOneStringsIndexZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[1].strings[0]",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
      ]
    },
    StructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[1].subStructs[0].subStructPrimitives.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
      ]
    },

    // anyStringEquals
    StringListAnyStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings",
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    EnumListAnyStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.enums",
                        expected: "one",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },

    // allStringEquals
    StringListAllStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    EnumListAllStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.enums",
                        expected: "one",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },

    // list slicing
    StringListStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[::2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2:]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[:4:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2::3]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[3:4]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStopStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2:4:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListNegativeStartStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[-2:-1]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartNegativeStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[1:-2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopNegativeStartSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[-3:3]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },

    // function: contains, list
    BooleanListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.booleans, primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListContainsIdentityProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.booleans[], primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.strings, primitives.string)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.integers, primitives.integer)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.enums, primitives.enum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.intEnums, primitives.intEnum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // function: contains, object projection
    BooleanMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.booleans.*, primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.strings.*, primitives.string)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.integers.*, primitives.integer)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.enums.*, primitives.enum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.intEnums.*, primitives.intEnum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // function: length, list
    BooleanListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.booleans) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.strings) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.integers) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.enums) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.intEnums) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // function: length, object projection
    BooleanMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.booleans.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.strings.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.integers.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.enums.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.intEnums.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // function: length, list filter
    // TODO: @ requires generic support for CurrentExpression, currently only recognized within flattens
    //BooleanListLengthFiltered: {
    //    acceptors: [
    //        {
    //            state: "success",
    //            matcher: {
    //                output: {
    //                    path: "length(lists.booleans[?@ == `true`]) > `0`"
    //                    expected: "true",
    //                    comparator: "booleanEquals"
    //                }
    //            }
    //        }
    //    ]
    //},

    // function: length, compound filter
    HasStructWithBoolean: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.boolean == `true`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithString: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.string == `\"foo\"`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithInteger: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.integer == `1`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnum: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.enum == `\"one\"`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithIntEnum: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.intEnum == `1`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithStringInStringList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(strings, primitives.string)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnumInEnumList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(enums, primitives.enum)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithStringInEnumList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(enums, primitives.string)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnumInStringList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(strings, primitives.enum)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // subfield projection
    HasStructWithStringByProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].primitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    HasStructWithSubstructWithStringByProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].subStructs[].subStructPrimitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    HasFilteredSubStruct: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].subStructs[?subStructPrimitives.integer > `0`][].subStructPrimitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/entities/{name}", code: 200)
operation GetEntity {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}

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
}

structure EntityPrimitives {
 // no defined comparison
 // blob: Blob,

    boolean: Boolean,
    string: String,
    byte: Byte,
    short: Short,
    integer: Integer,
    long: Long,
    float: Float,
    double: Double,

 // no defined comparison
 // bigInteger: BigInteger,
 // bigDecimal: BigDecimal,

 // no defined comparison
 // timestamp: Timestamp,

    enum: Enum,
    intEnum: IntEnum,
}

structure EntityLists {
    booleans: BooleanList,
    strings: StringList,
    integers: IntegerList,
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

list IntegerList {
    member: Integer,
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
    enums: EnumList,
    subStructs: SubStructList,
}

list SubStructList {
    member: SubStruct,
}

structure SubStruct {
    subStructPrimitives: EntityPrimitives,
}
