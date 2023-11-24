/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.model.Enum
import com.test.utils.successTest
import com.test.waiters.*
import kotlin.test.Test

class FunctionLengthTest {
    // list
    @Test
    fun testBooleanListLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanListLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { booleans = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { booleans = listOf(true) }
        },
    )

    @Test
    fun testStringListLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilStringListLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { strings = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { strings = listOf("foo") }
        },
    )

    @Test
    fun testIntegerListLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerListLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { integers = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { integers = listOf(0) }
        },
    )

    @Test
    fun testEnumListLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilEnumListLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { enums = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { enums = listOf(Enum.One) }
        },
    )

    @Test
    fun testIntEnumListLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilIntEnumListLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { intEnums = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { intEnums = listOf(IntEnum.One) }
        },
    )

    // object projection
    @Test
    fun testBooleanMapLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanMapLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { maps = EntityMaps { } },
        GetFunctionLengthResponse {
            maps = EntityMaps { booleans = mapOf() }
        },
        GetFunctionLengthResponse {
            maps = EntityMaps { booleans = mapOf("" to true) }
        },
    )

    @Test
    fun testStringMapLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilStringMapLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { maps = EntityMaps { } },
        GetFunctionLengthResponse {
            maps = EntityMaps { strings = mapOf() }
        },
        GetFunctionLengthResponse {
            maps = EntityMaps { strings = mapOf("" to "foo") }
        },
    )

    @Test
    fun testIntegerMapLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerMapLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { maps = EntityMaps { } },
        GetFunctionLengthResponse {
            maps = EntityMaps { integers = mapOf() }
        },
        GetFunctionLengthResponse {
            maps = EntityMaps { integers = mapOf("" to 0) }
        },
    )

    @Test
    fun testEnumMapLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilEnumMapLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { maps = EntityMaps { } },
        GetFunctionLengthResponse {
            maps = EntityMaps { enums = mapOf() }
        },
        GetFunctionLengthResponse {
            maps = EntityMaps { enums = mapOf("" to Enum.One) }
        },
    )

    @Test
    fun testIntEnumMapLength() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilIntEnumMapLength,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { maps = EntityMaps { } },
        GetFunctionLengthResponse {
            maps = EntityMaps { intEnums = mapOf() }
        },
        GetFunctionLengthResponse {
            maps = EntityMaps { intEnums = mapOf("" to IntEnum.One) }
        },
    )

    // compound filter
    @Test
    fun testHasStructWithBoolean() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithBoolean,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { boolean = false } },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { boolean = false } },
                    Struct { primitives = EntityPrimitives { boolean = true } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithString() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithString,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                    Struct { primitives = EntityPrimitives { string = "foo" } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithInteger() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithInteger,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { integer = 0 } },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { integer = 0 } },
                    Struct { primitives = EntityPrimitives { integer = 1 } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithEnum() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithEnum,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { enum = Enum.Two } },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { enum = Enum.Two } },
                    Struct { primitives = EntityPrimitives { enum = Enum.One } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithIntEnum() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithIntEnum,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
                    Struct { primitives = EntityPrimitives { intEnum = IntEnum.One } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithStringInStringList() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithStringInStringList,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { strings = listOf() },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "qux" }
                        strings = listOf()
                    },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "qux" }
                        strings = listOf("baz")
                    },
                )
            }
        },
        GetFunctionLengthResponse {
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

    @Test
    fun testHasStructWithEnumInEnumList() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithEnumInEnumList,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        enums = listOf()
                    },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        enums = listOf(Enum.Two)
                    },
                )
            }
        },
        GetFunctionLengthResponse {
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

    @Test
    fun testHasStructWithStringInEnumList() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithStringInEnumList,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf()
                    },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf(Enum.Two)
                    },
                )
            }
        },
        GetFunctionLengthResponse {
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

    @Test
    fun testHasStructWithEnumInStringList() = successTest(
        GetFunctionLengthRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithEnumInStringList,
        GetFunctionLengthResponse { },
        GetFunctionLengthResponse { lists = EntityLists { } },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetFunctionLengthResponse {
            lists = EntityLists { structs = listOf(Struct {}) }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { enums = listOf() },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = Enum.One.value }
                        enums = listOf()
                    },
                )
            }
        },
        GetFunctionLengthResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { enum = Enum.One }
                        strings = listOf(Enum.Two.value)
                    },
                )
            }
        },
        GetFunctionLengthResponse {
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
}
