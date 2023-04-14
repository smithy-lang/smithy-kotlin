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
        results: List<GetEntityResponse>,
        block: suspend WaitersTestClient.(request: GetEntityRequest) -> Outcome<GetEntityResponse>,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetEntityRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // primitive equality
    @Test fun testBooleanEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { boolean = false } },
            GetEntityResponse { primitives = EntityPrimitives { boolean = true } },
        ),
        WaitersTestClient::waitUntilBooleanEquals,
    )
    @Test fun testBooleanEqualsByCompare() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { boolean = false } },
            GetEntityResponse { primitives = EntityPrimitives { boolean = true } },
        ),
        WaitersTestClient::waitUntilBooleanEqualsByCompare,
    )
    @Test fun testStringEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { string = "bar" } },
            GetEntityResponse { primitives = EntityPrimitives { string = "foo" } },
        ),
        WaitersTestClient::waitUntilStringEquals,
    )
    @Test fun testStringEqualsByCompare() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { string = "bar" } },
            GetEntityResponse { primitives = EntityPrimitives { string = "foo" } },
        ),
        WaitersTestClient::waitUntilStringEqualsByCompare,
    )
    @Test fun testByteEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { byte = 0x00 } },
            GetEntityResponse { primitives = EntityPrimitives { byte = 0x01 } },
        ),
        WaitersTestClient::waitUntilByteEquals,
    )
    @Test fun testShortEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { short = 1 } },
            GetEntityResponse { primitives = EntityPrimitives { short = 2 } },
        ),
        WaitersTestClient::waitUntilShortEquals,
    )
    @Test fun testIntegerEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { integer = 2 } },
            GetEntityResponse { primitives = EntityPrimitives { integer = 3 } },
        ),
        WaitersTestClient::waitUntilIntegerEquals,
    )
    @Test fun testLongEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { long = 3L } },
            GetEntityResponse { primitives = EntityPrimitives { long = 4L } },
        ),
        WaitersTestClient::waitUntilLongEquals,
    )
    @Test fun testFloatEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { float = 4f } },
            GetEntityResponse { primitives = EntityPrimitives { float = 5f } },
        ),
        WaitersTestClient::waitUntilFloatEquals,
    )
    @Test fun testDoubleEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { double = 5.0 } },
            GetEntityResponse { primitives = EntityPrimitives { double = 6.0 } },
        ),
        WaitersTestClient::waitUntilDoubleEquals,
    )
    @Test fun testEnumEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { enum = Enum.Two } },
            GetEntityResponse { primitives = EntityPrimitives { enum = Enum.One } },
        ),
        WaitersTestClient::waitUntilEnumEquals,
    )
    @Test fun testEnumEqualsByCompare() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { enum = Enum.Two } },
            GetEntityResponse { primitives = EntityPrimitives { enum = Enum.One } },
        ),
        WaitersTestClient::waitUntilEnumEqualsByCompare,
    )
    @Test fun testIntEnumEquals() = successTest(
        listOf(
            GetEntityResponse { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
            GetEntityResponse { primitives = EntityPrimitives { intEnum = IntEnum.One } },
        ),
        WaitersTestClient::waitUntilIntEnumEquals,
    )

    // anyStringEquals
    @Test fun testStringListAnyListStringEquals() = successTest(
        listOf(
            GetEntityResponse { lists = EntityLists { strings = listOf("bar", "baz") } },
            GetEntityResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
        ),
        WaitersTestClient::waitUntilStringListAnyStringEquals,
    )
    @Test fun testEnumListAnyStringEquals() = successTest(
        listOf(
            GetEntityResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.Two) } },
            GetEntityResponse { lists = EntityLists { enums = listOf(Enum.Two, Enum.One) } },
        ),
        WaitersTestClient::waitUntilEnumListAnyStringEquals,
    )

    // allStringEquals
    @Test fun testStringListAllStringEquals() = successTest(
        listOf(
            GetEntityResponse { lists = EntityLists { strings = listOf("foo", "bar") } },
            GetEntityResponse { lists = EntityLists { strings = listOf("foo", "foo") } },
        ),
        WaitersTestClient::waitUntilStringListAllStringEquals,
    )
    @Test fun testEnumListAllStringEquals() = successTest(
        listOf(
            GetEntityResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.Two) } },
            GetEntityResponse { lists = EntityLists { enums = listOf(Enum.One, Enum.One) } },
        ),
        WaitersTestClient::waitUntilEnumListAllStringEquals,
    )

    // function: contains, list
    @Test fun testBooleanListContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = true }
                lists = EntityLists { booleans = listOf(false, false) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = true }
                lists = EntityLists { booleans = listOf(false, true) }
            },
        ),
        WaitersTestClient::waitUntilBooleanListContains,
    )
    @Test fun testBooleanListContainsIdentityProjection() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = true }
                lists = EntityLists { booleans = listOf(false, false) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = true }
                lists = EntityLists { booleans = listOf(false, true) }
            },
        ),
        WaitersTestClient::waitUntilBooleanListContainsIdentityProjection,
    )
    @Test fun testStringListContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { string = "bar" }
                lists = EntityLists { strings = listOf("foo", "baz") }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { string = "bar" }
                lists = EntityLists { strings = listOf("foo", "bar") }
            },
        ),
        WaitersTestClient::waitUntilStringListContains,
    )
    @Test fun testIntegerListContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { integer = 10 }
                lists = EntityLists { integers = listOf(8, 9) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { integer = 10 }
                lists = EntityLists { integers = listOf(9, 10) }
            },
        ),
        WaitersTestClient::waitUntilIntegerListContains,
    )
    @Test fun testEnumListContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { enum = Enum.Two }
                lists = EntityLists { enums = listOf(Enum.One, Enum.One) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { enum = Enum.Two }
                lists = EntityLists { enums = listOf(Enum.One, Enum.Two) }
            },
        ),
        WaitersTestClient::waitUntilEnumListContains,
    )
    @Test fun testIntEnumListContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { intEnum = IntEnum.Two }
                lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.One) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { intEnum = IntEnum.Two }
                lists = EntityLists { intEnums = listOf(IntEnum.One, IntEnum.Two) }
            },
        ),
        WaitersTestClient::waitUntilIntEnumListContains,
    )

    // function: contains, object projection
    @Test fun testBooleanMapContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = false }
                maps = EntityMaps { booleans = mapOf("i" to true, "j" to true) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { boolean = false }
                maps = EntityMaps { booleans = mapOf("i" to true, "j" to false) }
            },
        ),
        WaitersTestClient::waitUntilBooleanMapContains,
    )
    @Test fun testStringMapContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { string = "bar" }
                maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "baz") }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { string = "bar" }
                maps = EntityMaps { strings = mapOf("i" to "foo", "j" to "bar") }
            },
        ),
        WaitersTestClient::waitUntilStringMapContains,
    )
    @Test fun testIntegerMapContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { integer = 10 }
                maps = EntityMaps { integers = mapOf("i" to 9, "j" to 11) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { integer = 10 }
                maps = EntityMaps { integers = mapOf("i" to 9, "j" to 10) }
            },
        ),
        WaitersTestClient::waitUntilIntegerMapContains,
    )
    @Test fun testEnumMapContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { enum = Enum.Two }
                maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.One) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { enum = Enum.Two }
                maps = EntityMaps { enums = mapOf("i" to Enum.One, "j" to Enum.Two) }
            },
        ),
        WaitersTestClient::waitUntilEnumMapContains,
    )
    @Test fun testIntEnumMapContains() = successTest(
        listOf(
            GetEntityResponse {
                primitives = EntityPrimitives { intEnum = IntEnum.Two }
                maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.One) }
            },
            GetEntityResponse {
                primitives = EntityPrimitives { intEnum = IntEnum.Two }
                maps = EntityMaps { intEnums = mapOf("i" to IntEnum.One, "j" to IntEnum.Two) }
            },
        ),
        WaitersTestClient::waitUntilIntEnumMapContains,
    )

    // function: length, list
    @Test fun testBooleanListLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { lists = EntityLists { } },
            GetEntityResponse {
                lists = EntityLists { booleans = listOf() }
            },
            GetEntityResponse {
                lists = EntityLists { booleans = listOf(true) }
            },
        ),
        WaitersTestClient::waitUntilBooleanListLength,
    )
    @Test fun testStringListLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { lists = EntityLists { } },
            GetEntityResponse {
                lists = EntityLists { strings = listOf() }
            },
            GetEntityResponse {
                lists = EntityLists { strings = listOf("foo") }
            },
        ),
        WaitersTestClient::waitUntilStringListLength,
    )
    @Test fun testIntegerListLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { lists = EntityLists { } },
            GetEntityResponse {
                lists = EntityLists { integers = listOf() }
            },
            GetEntityResponse {
                lists = EntityLists { integers = listOf(0) }
            },
        ),
        WaitersTestClient::waitUntilIntegerListLength,
    )
    @Test fun testEnumListLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { lists = EntityLists { } },
            GetEntityResponse {
                lists = EntityLists { enums = listOf() }
            },
            GetEntityResponse {
                lists = EntityLists { enums = listOf(Enum.One) }
            },
        ),
        WaitersTestClient::waitUntilEnumListLength,
    )
    @Test fun testIntEnumListLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { lists = EntityLists { } },
            GetEntityResponse {
                lists = EntityLists { intEnums = listOf() }
            },
            GetEntityResponse {
                lists = EntityLists { intEnums = listOf(IntEnum.One) }
            },
        ),
        WaitersTestClient::waitUntilIntEnumListLength,
    )

    // function: length, object projection
    @Test fun testBooleanMapLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { maps = EntityMaps { } },
            GetEntityResponse {
                maps = EntityMaps { booleans = mapOf() }
            },
            GetEntityResponse {
                maps = EntityMaps { booleans = mapOf("" to true) }
            },
        ),
        WaitersTestClient::waitUntilBooleanMapLength,
    )
    @Test fun testStringMapLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { maps = EntityMaps { } },
            GetEntityResponse {
                maps = EntityMaps { strings = mapOf() }
            },
            GetEntityResponse {
                maps = EntityMaps { strings = mapOf("" to "foo") }
            },
        ),
        WaitersTestClient::waitUntilStringMapLength,
    )
    @Test fun testIntegerMapLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { maps = EntityMaps { } },
            GetEntityResponse {
                maps = EntityMaps { integers = mapOf() }
            },
            GetEntityResponse {
                maps = EntityMaps { integers = mapOf("" to 0) }
            },
        ),
        WaitersTestClient::waitUntilIntegerMapLength,
    )
    @Test fun testEnumMapLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { maps = EntityMaps { } },
            GetEntityResponse {
                maps = EntityMaps { enums = mapOf() }
            },
            GetEntityResponse {
                maps = EntityMaps { enums = mapOf("" to Enum.One) }
            },
        ),
        WaitersTestClient::waitUntilEnumMapLength,
    )
    @Test fun testIntEnumMapLength() = successTest(
        listOf(
            GetEntityResponse { },
            GetEntityResponse { maps = EntityMaps { } },
            GetEntityResponse {
                maps = EntityMaps { intEnums = mapOf() }
            },
            GetEntityResponse {
                maps = EntityMaps { intEnums = mapOf("" to IntEnum.One) }
            },
        ),
        WaitersTestClient::waitUntilIntEnumMapLength,
    )

    // function: length, compound filter
    @Test fun testHasStructWithBoolean() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithBoolean,
    )
    @Test fun testHasStructWithString() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithString,
    )
    @Test fun testHasStructWithInteger() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithInteger,
    )
    @Test fun testHasStructWithEnum() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithEnum,
    )
    @Test fun testHasStructWithIntEnum() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithIntEnum,
    )
    @Test fun testHasStructWithStringInStringList() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithStringInStringList,
    )
    @Test fun testHasStructWithEnumInEnumList() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithEnumInEnumList,
    )
    @Test fun testHasStructWithStringInEnumList() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithStringInEnumList,
    )
    @Test fun testHasStructWithEnumInStringList() = successTest(
        listOf(
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
        ),
        WaitersTestClient::waitUntilHasStructWithEnumInStringList,
    )
}
