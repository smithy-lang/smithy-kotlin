/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.model.Enum
import com.test.waiters.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionLengthTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetFunctionLengthRequest) -> Outcome<GetFunctionLengthResponse>,
        vararg results: GetFunctionLengthResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetFunctionLengthRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // list
    @Test fun testBooleanListLength() = successTest(
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

    @Test fun testStringListLength() = successTest(
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

    @Test fun testIntegerListLength() = successTest(
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

    @Test fun testEnumListLength() = successTest(
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

    @Test fun testIntEnumListLength() = successTest(
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
    @Test fun testBooleanMapLength() = successTest(
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

    @Test fun testStringMapLength() = successTest(
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

    @Test fun testIntegerMapLength() = successTest(
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

    @Test fun testEnumMapLength() = successTest(
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

    @Test fun testIntEnumMapLength() = successTest(
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
    @Test fun testHasStructWithBoolean() = successTest(
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

    @Test fun testHasStructWithString() = successTest(
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

    @Test fun testHasStructWithInteger() = successTest(
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

    @Test fun testHasStructWithEnum() = successTest(
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

    @Test fun testHasStructWithIntEnum() = successTest(
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

    @Test fun testHasStructWithStringInStringList() = successTest(
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

    @Test fun testHasStructWithEnumInEnumList() = successTest(
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

    @Test fun testHasStructWithStringInEnumList() = successTest(
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

    @Test fun testHasStructWithEnumInStringList() = successTest(
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
