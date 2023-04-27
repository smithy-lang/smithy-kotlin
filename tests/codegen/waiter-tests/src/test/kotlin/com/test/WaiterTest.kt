/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import aws.smithy.kotlin.runtime.retries.getOrThrow
import aws.smithy.kotlin.runtime.retries.Outcome
import com.test.model.*
import com.test.model.Enum
import com.test.waiters.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WaiterTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetEntityRequest) -> Outcome<GetEntityResponse>,
        vararg results: GetEntityResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetEntityRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // primitive equality
    @Test fun testBooleanEquals() = successTest(
        WaitersTestClient::waitUntilBooleanEquals,
        GetEntityResponse { primitives = EntityPrimitives { boolean = false } },
        GetEntityResponse { primitives = EntityPrimitives { boolean = true } },
    )
    @Test fun testBooleanEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilBooleanEqualsByCompare,
        GetEntityResponse { primitives = EntityPrimitives { boolean = false } },
        GetEntityResponse { primitives = EntityPrimitives { boolean = true } },
    )
    @Test fun testStringEquals() = successTest(
        WaitersTestClient::waitUntilStringEquals,
        GetEntityResponse { primitives = EntityPrimitives { string = "bar" } },
        GetEntityResponse { primitives = EntityPrimitives { string = "foo" } },
    )
    @Test fun testStringEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilStringEqualsByCompare,
        GetEntityResponse { primitives = EntityPrimitives { string = "bar" } },
        GetEntityResponse { primitives = EntityPrimitives { string = "foo" } },
    )
    @Test fun testByteEquals() = successTest(
        WaitersTestClient::waitUntilByteEquals,
        GetEntityResponse { primitives = EntityPrimitives { byte = 0x00 } },
        GetEntityResponse { primitives = EntityPrimitives { byte = 0x01 } },
    )
    @Test fun testShortEquals() = successTest(
        WaitersTestClient::waitUntilShortEquals,
        GetEntityResponse { primitives = EntityPrimitives { short = 1 } },
        GetEntityResponse { primitives = EntityPrimitives { short = 2 } },
    )
    @Test fun testIntegerEquals() = successTest(
        WaitersTestClient::waitUntilIntegerEquals,
        GetEntityResponse { primitives = EntityPrimitives { integer = 2 } },
        GetEntityResponse { primitives = EntityPrimitives { integer = 3 } },
    )
    @Test fun testLongEquals() = successTest(
        WaitersTestClient::waitUntilLongEquals,
        GetEntityResponse { primitives = EntityPrimitives { long = 3L } },
        GetEntityResponse { primitives = EntityPrimitives { long = 4L } },
    )
    @Test fun testFloatEquals() = successTest(
        WaitersTestClient::waitUntilFloatEquals,
        GetEntityResponse { primitives = EntityPrimitives { float = 4f } },
        GetEntityResponse { primitives = EntityPrimitives { float = 5f } },
    )
    @Test fun testDoubleEquals() = successTest(
        WaitersTestClient::waitUntilDoubleEquals,
        GetEntityResponse { primitives = EntityPrimitives { double = 5.0 } },
        GetEntityResponse { primitives = EntityPrimitives { double = 6.0 } },
    )
    @Test fun testEnumEquals() = successTest(
        WaitersTestClient::waitUntilEnumEquals,
        GetEntityResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetEntityResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )
    @Test fun testEnumEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilEnumEqualsByCompare,
        GetEntityResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetEntityResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )
    @Test fun testIntEnumEquals() = successTest(
        WaitersTestClient::waitUntilIntEnumEquals,
        GetEntityResponse { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
        GetEntityResponse { primitives = EntityPrimitives { intEnum = IntEnum.One } },
    )

    // anyStringEquals
    @Test fun testStringListAnyListStringEquals() = successTest(
        WaitersTestClient::waitUntilStringListAnyStringEquals,
        GetEntityResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
        GetEntityResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
    )
    @Test fun testEnumListAnyStringEquals() = successTest(
        WaitersTestClient::waitUntilEnumListAnyStringEquals,
        GetEntityResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.Two) } },
        GetEntityResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.One) } },
    )

    // allStringEquals
    @Test fun testStringListAllStringEquals() = successTest(
        WaitersTestClient::waitUntilStringListAllStringEquals,
        GetEntityResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
        GetEntityResponse { lists = EntityLists { strings = listOf("foo", "foo") } },
    )
    @Test fun testEnumListAllStringEquals() = successTest(
        WaitersTestClient::waitUntilEnumListAllStringEquals,
        GetEntityResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.Two) } },
        GetEntityResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.One) } },
    )

    // function: contains, list
    @Test fun testBooleanListContains() = successTest(
        WaitersTestClient::waitUntilBooleanListContains,
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, false) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, true) }
        },
    )
    @Test fun testBooleanListContainsIdentityProjection() = successTest(
        WaitersTestClient::waitUntilBooleanListContainsIdentityProjection,
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, false) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = true }
            lists = EntityLists { booleans = listOf(false, true) }
        },
    )
    @Test fun testStringListContains() = successTest(
        WaitersTestClient::waitUntilStringListContains,
        GetEntityResponse {
            primitives = EntityPrimitives { string = "bar" }
            lists = EntityLists { strings = listOf("foo", "baz") }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { string = "bar" }
            lists = EntityLists { strings = listOf("foo", "bar") }
        },
    )
    @Test fun testIntegerListContains() = successTest(
        WaitersTestClient::waitUntilIntegerListContains,
        GetEntityResponse {
            primitives = EntityPrimitives { integer = 10 }
            lists = EntityLists { integers = listOf(8, 9) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { integer = 10 }
            lists = EntityLists { integers = listOf(9, 10) }
        },
    )
    @Test fun testEnumListContains() = successTest(
        WaitersTestClient::waitUntilEnumListContains,
        GetEntityResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            lists = EntityLists { enums = listOf(Enum.One, Enum.One) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            lists = EntityLists { enums = listOf(Enum.One, Enum.Two) }
        },
    )
    @Test fun testIntEnumListContains() = successTest(
        WaitersTestClient::waitUntilIntEnumListContains,
        GetEntityResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.One) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.Two) }
        },
    )

    // function: contains, object projection
    @Test fun testBooleanMapContains() = successTest(
        WaitersTestClient::waitUntilBooleanMapContains,
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = false }
            maps = EntityMaps { booleans = mapOf("i" to true, "j" to true) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { boolean = false }
            maps = EntityMaps { booleans = mapOf("i" to true, "j" to false) }
        },
    )
    @Test fun testStringMapContains() = successTest(
        WaitersTestClient::waitUntilStringMapContains,
        GetEntityResponse {
            primitives = EntityPrimitives { string = "bar" }
            maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "baz") }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { string = "bar" }
            maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "bar") }
        },
    )
    @Test fun testIntegerMapContains() = successTest(
        WaitersTestClient::waitUntilIntegerMapContains,
        GetEntityResponse {
            primitives = EntityPrimitives { integer = 10 }
            maps = EntityMaps { integers = mapOf("i" to 9, "j" to 11) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { integer = 10 }
            maps = EntityMaps { integers = mapOf("i" to 9, "j" to 10) }
        },
    )
    @Test fun testEnumMapContains() = successTest(
        WaitersTestClient::waitUntilEnumMapContains,
        GetEntityResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.One) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { enum = Enum.Two }
            maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.Two) }
        },
    )
    @Test fun testIntEnumMapContains() = successTest(
        WaitersTestClient::waitUntilIntEnumMapContains,
        GetEntityResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.One) }
        },
        GetEntityResponse {
            primitives = EntityPrimitives { intEnum = IntEnum.Two }
            maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.Two) }
        },
    )

    // function: length, list
    @Test fun testBooleanListLength() = successTest(
        WaitersTestClient::waitUntilBooleanListLength,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { booleans = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { booleans = listOf(true) }
        },
    )
    @Test fun testStringListLength() = successTest(
        WaitersTestClient::waitUntilStringListLength,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { strings = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { strings = listOf("foo") }
        },
    )
    @Test fun testIntegerListLength() = successTest(
        WaitersTestClient::waitUntilIntegerListLength,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { integers = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { integers = listOf(0) }
        },
    )
    @Test fun testEnumListLength() = successTest(
        WaitersTestClient::waitUntilEnumListLength,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { enums = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { enums = listOf(Enum.One) }
        },
    )
    @Test fun testIntEnumListLength() = successTest(
        WaitersTestClient::waitUntilIntEnumListLength,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { intEnums = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { intEnums = listOf(IntEnum.One) }
        },
    )

    // function: length, object projection
    @Test fun testBooleanMapLength() = successTest(
        WaitersTestClient::waitUntilBooleanMapLength,
        GetEntityResponse { },
        GetEntityResponse { maps = EntityMaps { } },
        GetEntityResponse {
            maps = EntityMaps { booleans = mapOf() }
        },
        GetEntityResponse {
            maps = EntityMaps { booleans = mapOf("" to true) }
        },
    )
    @Test fun testStringMapLength() = successTest(
        WaitersTestClient::waitUntilStringMapLength,
        GetEntityResponse { },
        GetEntityResponse { maps = EntityMaps { } },
        GetEntityResponse {
            maps = EntityMaps { strings = mapOf() }
        },
        GetEntityResponse {
            maps = EntityMaps { strings = mapOf("" to "foo") }
        },
    )
    @Test fun testIntegerMapLength() = successTest(
        WaitersTestClient::waitUntilIntegerMapLength,
        GetEntityResponse { },
        GetEntityResponse { maps = EntityMaps { } },
        GetEntityResponse {
            maps = EntityMaps { integers = mapOf() }
        },
        GetEntityResponse {
            maps = EntityMaps { integers = mapOf("" to 0) }
        },
    )
    @Test fun testEnumMapLength() = successTest(
        WaitersTestClient::waitUntilEnumMapLength,
        GetEntityResponse { },
        GetEntityResponse { maps = EntityMaps { } },
        GetEntityResponse {
            maps = EntityMaps { enums = mapOf() }
        },
        GetEntityResponse {
            maps = EntityMaps { enums = mapOf("" to Enum.One) }
        },
    )
    @Test fun testIntEnumMapLength() = successTest(
        WaitersTestClient::waitUntilIntEnumMapLength,
        GetEntityResponse { },
        GetEntityResponse { maps = EntityMaps { } },
        GetEntityResponse {
            maps = EntityMaps { intEnums = mapOf() }
        },
        GetEntityResponse {
            maps = EntityMaps { intEnums = mapOf("" to IntEnum.One) }
        },
    )

    // function: length, compound filter
    @Test fun testHasStructWithBoolean() = successTest(
        WaitersTestClient::waitUntilHasStructWithBoolean,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { boolean = false } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { boolean = false } },
                    Struct { primitives = EntityPrimitives { boolean = true } },
                )
            }
        },
    )
    @Test fun testHasStructWithString() = successTest(
        WaitersTestClient::waitUntilHasStructWithString,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                    Struct { primitives = EntityPrimitives { string = "foo" } },
                )
            }
        },
    )
    @Test fun testHasStructWithInteger() = successTest(
        WaitersTestClient::waitUntilHasStructWithInteger,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { integer = 0 } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { integer = 0 } },
                    Struct { primitives = EntityPrimitives { integer = 1 } },
                )
            }
        },
    )
    @Test fun testHasStructWithEnum() = successTest(
        WaitersTestClient::waitUntilHasStructWithEnum,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { enum = Enum.Two } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { enum = Enum.Two } },
                    Struct { primitives = EntityPrimitives { enum = Enum.One } },
                )
            }
        },
    )
    @Test fun testHasStructWithIntEnum() = successTest(
        WaitersTestClient::waitUntilHasStructWithIntEnum,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.One } },
                )
            }
        },
    )
    @Test fun testHasStructWithStringInStringList() = successTest(
        WaitersTestClient::waitUntilHasStructWithStringInStringList,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { strings = listOf() },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "qux" }
                        strings = listOf()
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "qux" }
                        strings = listOf("baz")
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "qux" }
                        strings = listOf("baz", "qux")
                    },
                )
            }
        },
    )
    @Test fun testHasStructWithEnumInEnumList() = successTest(
        WaitersTestClient::waitUntilHasStructWithEnumInEnumList,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        enums = listOf()
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        enums = listOf(Enum.Two)
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        enums = listOf(Enum.Two, Enum.One)
                    },
                )
            }
        },
    )
    @Test fun testHasStructWithStringInEnumList() = successTest(
        WaitersTestClient::waitUntilHasStructWithStringInEnumList,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf()
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf(Enum.Two)
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf(Enum.Two, Enum.One)
                    },
                )
            }
        },
    )
    @Test fun testHasStructWithEnumInStringList() = successTest(
        WaitersTestClient::waitUntilHasStructWithEnumInStringList,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf()
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        strings = listOf(Enum.Two.value)
                    },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        strings = listOf(Enum.Two.value, Enum.One.value)
                    },
                )
            }
        },
    )

    // subfield projection
    @Test fun testHasStructWithStringByProjection() = successTest(
        WaitersTestClient::waitUntilHasStructWithStringByProjection,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists { structs = listOf(Struct { }) }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                    Struct { primitives = EntityPrimitives { string = "foo" } },
                )
            }
        },
    )
    @Test fun testHasStructWithSubstructWithStringByProjection() = successTest(
        WaitersTestClient::waitUntilHasStructWithSubstructWithStringByProjection,
        GetEntityResponse { },
        GetEntityResponse { lists = EntityLists { } },
        GetEntityResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { }) },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "bar" } }) },
                    Struct { subStructs = listOf(SubStruct { }) },
                )
            }
        },
        GetEntityResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        subStructs = listOf(
                            SubStruct { subStructPrimitives = EntityPrimitives { string = "bar" } },
                            SubStruct { subStructPrimitives = EntityPrimitives { string = "baz" } },
                        )
                    },
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "foo" } }) },
                )
            }
        },
    )
}
