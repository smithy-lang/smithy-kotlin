/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalStdlibApi::class)
class XmlSerdeProviderTest {

    @Test
    fun `it instantiates a serializer`() {
        val unit = XmlSerdeProvider()

        assertNotNull(unit.serializer())
    }

    @Test
    fun `it instantiates a deserializer`() {
        val unit = XmlSerdeProvider()

        assertNotNull(unit.deserializer("<a>boo</a>".encodeToByteArray()))
    }
}
