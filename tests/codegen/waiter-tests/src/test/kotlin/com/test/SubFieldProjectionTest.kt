/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.utils.successTest
import com.test.waiters.waitUntilHasFilteredSubStruct
import com.test.waiters.waitUntilHasStructWithStringByProjection
import com.test.waiters.waitUntilHasStructWithSubstructWithStringByProjection
import kotlin.test.Test

class SubFieldProjectionTest {
    @Test
    fun testHasStructWithStringByProjection() = successTest(
        GetSubFieldProjectionRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithStringByProjection,
        GetSubFieldProjectionResponse { },
        GetSubFieldProjectionResponse { lists = EntityLists { } },
        GetSubFieldProjectionResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists { structs = listOf(Struct { }) }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                )
            }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { primitives = EntityPrimitives { string = "bar" } },
                    Struct { primitives = EntityPrimitives { string = "foo" } },
                )
            }
        },
    )

    @Test
    fun testHasStructWithSubstructWithStringByProjection() = successTest(
        GetSubFieldProjectionRequest { name = "test" },
        WaitersTestClient::waitUntilHasStructWithSubstructWithStringByProjection,
        GetSubFieldProjectionResponse { },
        GetSubFieldProjectionResponse { lists = EntityLists { } },
        GetSubFieldProjectionResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { }) },
                )
            }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "bar" } }) },
                    Struct { subStructs = listOf(SubStruct { }) },
                )
            }
        },
        GetSubFieldProjectionResponse {
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

    @Test
    fun testHasFilteredSubStruct() = successTest(
        GetSubFieldProjectionRequest { name = "test" },
        WaitersTestClient::waitUntilHasFilteredSubStruct,
        GetSubFieldProjectionResponse { },
        GetSubFieldProjectionResponse { lists = EntityLists { } },
        GetSubFieldProjectionResponse {
            lists = EntityLists { structs = listOf() }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { }) },
                )
            }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "foo" } }) },
                )
            }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "foo"; integer = -1 } }) },
                    Struct { subStructs = listOf(SubStruct { subStructPrimitives = EntityPrimitives { string = "bar"; integer = 2 } }) },
                )
            }
        },
        GetSubFieldProjectionResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        subStructs = listOf(
                            SubStruct { subStructPrimitives = EntityPrimitives { string = "foo"; integer = -1 } },
                            SubStruct { subStructPrimitives = EntityPrimitives { string = "bar"; integer = 2 } },
                        )
                    },
                    Struct {
                        subStructs = listOf(
                            SubStruct { subStructPrimitives = EntityPrimitives { string = "foo"; integer = 2 } },
                        )
                    },
                )
            }
        },
    )
}
