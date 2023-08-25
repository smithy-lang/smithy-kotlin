/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.waiters.waitUntilStructListStringListMultiSelectList
import com.test.waiters.waitUntilStructListStringMultiSelectList
import com.test.waiters.waitUntilStructListSubStructPrimitivesBooleanMultiSelectList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MultiSelectListTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetMultiSelectListRequest) -> Outcome<GetMultiSelectListResponse>,
        vararg results: GetMultiSelectListResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetMultiSelectListRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    // multi select list
    @Test fun testStructListStringMultiSelectList() = successTest(
        WaitersTestClient::waitUntilStructListStringMultiSelectList,
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
    )

    @Test fun testStructListStringListMultiSelectList() = successTest(
        WaitersTestClient::waitUntilStructListStringListMultiSelectList,
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
    )

    @Test fun testStructListSubStructPrimitivesBooleanMultiSelectList() = successTest(
        WaitersTestClient::waitUntilStructListSubStructPrimitivesBooleanMultiSelectList,
        GetMultiSelectListResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        subStructs = listOf(
                            SubStruct {
                                subStructPrimitives = EntityPrimitives { boolean = true }
                            },
                        )
                    },
                    Struct {
                        subStructs = listOf(
                            SubStruct {
                                subStructPrimitives = EntityPrimitives { boolean = false }
                            },
                        )
                    },
                )
            }
        },
        GetMultiSelectListResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        subStructs = listOf(
                            SubStruct {
                                subStructPrimitives = EntityPrimitives { boolean = false }
                            },
                        )
                    },
                    Struct {
                        subStructs = listOf(
                            SubStruct {
                                subStructPrimitives = EntityPrimitives { boolean = true }
                            },
                        )
                    },
                )
            }
        },
    )
}
