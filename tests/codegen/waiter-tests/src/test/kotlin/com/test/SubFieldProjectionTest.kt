/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.waiters.waitUntilHasFilteredSubStruct
import com.test.waiters.waitUntilHasStructWithStringByProjection
import com.test.waiters.waitUntilHasStructWithSubstructWithStringByProjection
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubFieldProjectionTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetSubFieldProjectionRequest) -> Outcome<GetSubFieldProjectionResponse>,
        vararg results: GetSubFieldProjectionResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetSubFieldProjectionRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test
    fun testHasStructWithStringByProjection() = successTest(
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

    @Test fun testHasStructWithSubstructWithStringByProjection() = successTest(
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

    @Test fun testHasFilteredSubStruct() = successTest(
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
