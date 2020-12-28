/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.json

import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalStdlibApi::class)
class JsonSerdeProviderTest {

    @Test
    fun itInstantiatesASerializer() {
        val unit = JsonSerdeProvider()

        assertNotNull(unit.serializer())
    }

    @Test
    fun itInstantiatesADeserializer() {
        val unit = JsonSerdeProvider()

        assertNotNull(unit.deserializer("{}".encodeToByteArray()))
    }
}
