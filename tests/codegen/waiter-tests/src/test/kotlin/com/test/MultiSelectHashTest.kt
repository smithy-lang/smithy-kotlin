/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.utils.successTest
import com.test.waiters.waitUntilStructListStringMultiSelectHash
import com.test.waiters.waitUntilStructListStringsAnyMultiSelectHash
import com.test.waiters.waitUntilStructListStringsMultiSelectHash
import com.test.waiters.waitUntilStructListSubStructPrimitivesBooleanMultiSelectHash
import org.junit.jupiter.api.Test

class MultiSelectHashTest {
    @Test fun testStructListStringMultiSelectHash() = successTest(
        GetMultiSelectHashRequest { name = "test" },
        WaitersTestClient::waitUntilStructListStringMultiSelectHash,
        GetMultiSelectHashResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
        GetMultiSelectHashResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
    )

    @Test fun testStructListStringsMultiSelectHash() = successTest(
        GetMultiSelectHashRequest { name = "test" },
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
        GetMultiSelectHashRequest { name = "test" },
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
        GetMultiSelectHashRequest { name = "test" },
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
