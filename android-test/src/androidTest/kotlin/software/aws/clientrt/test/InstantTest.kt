/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.test

import androidx.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import software.aws.clientrt.time.Instant
import software.aws.clientrt.time.TimestampFormat
import kotlin.test.assertEquals

/**
 * Instant relies on JDK8 java.time.* libraries. Ensure core library desugaring works and that
 * we didn't rely on anything not available through D8.
 */
@RunWith(AndroidJUnit4::class)
class InstantTest {
    @Test
    fun instantParsesAndFormatsInAllForms() {
        val expected = Instant.fromEpochSeconds(1604604157, 0)

        val iso8601Ts = "2020-11-05T19:22:37Z"
        val actualIso = Instant.fromIso8601(iso8601Ts)
        assertEquals(expected.epochSeconds, actualIso.epochSeconds, "failed to correctly parse $iso8601Ts sec")
        assertEquals(expected.nanosecondsOfSecond, actualIso.nanosecondsOfSecond, "failed to correctly parse $iso8601Ts ns")
        assertEquals(iso8601Ts, expected.format(TimestampFormat.ISO_8601))

        val rfc5322Ts = "Thu, 05 Nov 2020 19:22:37 GMT"
        val actual5322 = Instant.fromRfc5322(rfc5322Ts)
        assertEquals(expected.epochSeconds, actual5322.epochSeconds, "failed to correctly parse $rfc5322Ts sec")
        assertEquals(expected.nanosecondsOfSecond, actual5322.nanosecondsOfSecond, "failed to correctly parse $rfc5322Ts ns")
        assertEquals(rfc5322Ts, expected.format(TimestampFormat.RFC_5322))

        val epochTs = "1604604157"
        // TODO - string overload is added in integration branch. Re-enable test when merged
        // val actualEpoch = Instant.fromEpochSeconds(epochTs)
        // assertEquals(expected, actualEpoch, "failed to correctly parse $epochTs")
        assertEquals(epochTs, expected.format(TimestampFormat.EPOCH_SECONDS))
    }
}