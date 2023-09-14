/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*
import com.test.model.Enum
import com.test.utils.successTest
import com.test.waiters.*
import org.junit.jupiter.api.Test

class PrimitiveEqualityTest {
    @Test fun testBooleanEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = false } },
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = true } },
    )

    @Test fun testBooleanEqualsByCompare() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilBooleanEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = false } },
        GetPrimitiveResponse { primitives = EntityPrimitives { boolean = true } },
    )

    @Test fun testStringEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilStringEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "bar" } },
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "foo" } },
    )

    @Test fun testStringEqualsByCompare() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilStringEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "bar" } },
        GetPrimitiveResponse { primitives = EntityPrimitives { string = "foo" } },
    )

    @Test fun testByteEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilByteEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { byte = 0x00 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { byte = 0x01 } },
    )

    @Test fun testShortEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilShortEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { short = 1 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { short = 2 } },
    )

    @Test fun testIntegerEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilIntegerEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { integer = 2 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { integer = 3 } },
    )

    @Test fun testLongEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilLongEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { long = 3L } },
        GetPrimitiveResponse { primitives = EntityPrimitives { long = 4L } },
    )

    @Test fun testFloatEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilFloatEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { float = 4f } },
        GetPrimitiveResponse { primitives = EntityPrimitives { float = 5f } },
    )

    @Test fun testDoubleEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilDoubleEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { double = 5.0 } },
        GetPrimitiveResponse { primitives = EntityPrimitives { double = 6.0 } },
    )

    @Test fun testEnumEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilEnumEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )

    @Test fun testEnumEqualsByCompare() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilEnumEqualsByCompare,
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { enum = Enum.One } },
    )

    @Test fun testIntEnumEquals() = successTest(
        GetPrimitiveRequest { name = "test" },
        WaitersTestClient::waitUntilIntEnumEquals,
        GetPrimitiveResponse { primitives = EntityPrimitives { intEnum = IntEnum.Two } },
        GetPrimitiveResponse { primitives = EntityPrimitives { intEnum = IntEnum.One } },
    )
}
