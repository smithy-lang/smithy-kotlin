/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.ExperimentalApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(ExperimentalApi::class)
class EmfTelemetryProviderTest {
    @Test
    fun rejectsEmptyNamespace() {
        assertFailsWith<IllegalArgumentException> {
            EmfTelemetryProvider { namespace = "" }
        }
    }

    @Test
    fun rejectsNamespaceOver1024Chars() {
        assertFailsWith<IllegalArgumentException> {
            EmfTelemetryProvider { namespace = "a".repeat(1025) }
        }
    }

    @Test
    fun rejectsLogGroupNameOver512Chars() {
        assertFailsWith<IllegalArgumentException> {
            EmfTelemetryProvider { logGroupName = "a".repeat(513) }
        }
    }

    @Test
    fun acceptsValidConfiguration() {
        val provider = EmfTelemetryProvider {
            namespace = "MyApp"
            logGroupName = "/aws/lambda/my-fn"
        }
        assertNotNull(provider.meterProvider)
    }

    @Test
    fun acceptsNullLogGroupName() {
        val provider = EmfTelemetryProvider {
            namespace = "MyApp"
            logGroupName = null
        }
        assertNotNull(provider.meterProvider)
    }

    @Test
    fun acceptsMaxLengthNamespace() {
        val provider = EmfTelemetryProvider {
            namespace = "a".repeat(1024)
            logGroupName = null
        }
        assertNotNull(provider.meterProvider)
    }

    @Test
    fun acceptsMaxLengthLogGroupName() {
        val provider = EmfTelemetryProvider {
            namespace = "MyApp"
            logGroupName = "a".repeat(512)
        }
        assertNotNull(provider.meterProvider)
    }
}
