/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.utils.successTest
import com.test.waiters.waitUntilStructListStringListMultiSelectList
import com.test.waiters.waitUntilStructListStringMultiSelectList
import com.test.waiters.waitUntilStructListSubStructPrimitivesBooleanMultiSelectList
import org.junit.jupiter.api.Test

class MultiSelectListTest {
    @Test fun testStructListStringMultiSelectList() = successTest(
        GetMultiSelectListRequest { name = "test" },
        WaitersTestClient::waitUntilStructListStringMultiSelectList,
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
    )

    @Test fun testStructListStringListMultiSelectList() = successTest(
        GetMultiSelectListRequest { name = "test" },
        WaitersTestClient::waitUntilStructListStringListMultiSelectList,
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "foo" } }, Struct { primitives = EntityPrimitives { string = "bar" } }) } },
        GetMultiSelectListResponse { lists = EntityLists { structs = listOf(Struct { primitives = EntityPrimitives { string = "bar" } }, Struct { primitives = EntityPrimitives { string = "foo" } }) } },
    )

    @Test fun testStructListSubStructPrimitivesBooleanMultiSelectList() = successTest(
        GetMultiSelectListRequest { name = "test" },
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
