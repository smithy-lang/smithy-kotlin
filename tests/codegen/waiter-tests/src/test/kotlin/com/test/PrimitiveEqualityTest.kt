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

class PrimitiveEqualityTest {
    private fun successTest(
        block: suspend WaitersTestClient.(request: GetPrimitiveRequest) -> Outcome<GetPrimitiveResponse>,
        vararg results: GetPrimitiveResponse,
    ): Unit = runTest {
        val client = DefaultWaitersTestClient(results.map { Result.success(it) })
        val req = GetPrimitiveRequest { name = "test" }

        val outcome = client.block(req)
        assertEquals(results.size, outcome.attempts)
        assertEquals(results.last(), outcome.getOrThrow())
    }

    @Test fun testBooleanEquals() = successTest(
        WaitersTestClient::waitUntilBooleanEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = false } },
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = true } },
    )

    @Test fun testBooleanEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilBooleanEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = false } },
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = true } },
    )

    @Test fun testStringEquals() = successTest(
        WaitersTestClient::waitUntilStringEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "bar" } },
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "foo" } },
    )

    @Test fun testStringEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilStringEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "bar" } },
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "foo" } },
    )

    @Test fun testByteEquals() = successTest(
        WaitersTestClient::waitUntilByteEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { byte = 0x00 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { byte = 0x01 } },
    )

    @Test fun testShortEquals() = successTest(
        WaitersTestClient::waitUntilShortEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { short = 1 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testIntegerEquals() = successTest(
        WaitersTestClient::waitUntilIntegerEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { integer = 2 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { integer = 3 } },
    )

    @Test fun testLongEquals() = successTest(
        WaitersTestClient::waitUntilLongEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { long = 3L } },
        GetPrimitiveResponse { primitives = EntityPrimitives { long = 4L } },
    )

    @Test fun testFloatEquals() = successTest(
        WaitersTestClient::waitUntilFloatEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { float = 4f } },
        GetPrimitiveResponse { primitives = EntityPrimitives { float = 5f } },
    )

    @Test fun testDoubleEquals() = successTest(
        WaitersTestClient::waitUntilDoubleEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { double = 5.0 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { double = 6.0 } },
    )

    @Test fun testEnumEquals() = successTest(
        WaitersTestClient::waitUntilEnumEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )

    @Test fun testEnumEqualsByCompare() = successTest(
        WaitersTestClient::waitUntilEnumEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )

    @Test fun testIntEnumEquals() = successTest(
        WaitersTestClient::waitUntilIntEnumEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { intEnum = IntEnum.One } },
    )
}
