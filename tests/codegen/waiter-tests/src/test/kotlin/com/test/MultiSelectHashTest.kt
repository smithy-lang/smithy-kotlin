/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.getOrThrow
import com.test.model.*
import com.test.waiters.waitUntilStructListStringMultiSelectHash
import com.test.waiters.waitUntilStructListStringsAnyMultiSelectHash
import com.test.waiters.waitUntilStructListStringsMultiSelectHash
import com.test.waiters.waitUntilStructListSubStructPrimitivesBooleanMultiSelectHash
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MultiSelectHashTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetMultiSelectHashRequest) -> Outcome<GetMultiSelectHashResponse>,
        vararg results: GetMultiSelectHashResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetMultiSelectHashRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testStructListStringMultiSelectHash() = successTest(
        WaitersTestClient::waitUntilStructListStringMultiSelectHash,
        GetMultiSelectHashResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
        GetMultiSelectHashResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
    )

    @Test fun testStructListStringsMultiSelectHash() = successTest(
        WaitersTestClient::waitUntilStructListStringsMultiSelectHash,
        GetMultiSelectHashResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                )
            }
        },
        GetMultiSelectHashResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("foo")
                    },
                )
            }
        },
    )

    @Test fun testStructListStringsAnyMultiSelectHash() = successTest(
        WaitersTestClient::waitUntilStructListStringsAnyMultiSelectHash,
        GetMultiSelectHashResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                )
            }
        },
        GetMultiSelectHashResponse {
            lists = EntityLists {
                structs = listOf(
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar")
                    },
                    Struct {
                        primitives = EntityPrimitives { string = "bar" }
                        strings = listOf("bar", "bar", "foo")
                    },
                )
            }
        },
    )

    @Test fun testStructListSubStructPrimitivesBooleanMultiSelectHash() = successTest(
        WaitersTestClient::waitUntilStructListSubStructPrimitivesBooleanMultiSelectHash,
        GetMultiSelectHashResponse {
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
        GetMultiSelectHashResponse {
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
    )
}
