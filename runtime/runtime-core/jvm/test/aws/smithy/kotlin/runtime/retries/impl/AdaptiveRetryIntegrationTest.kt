/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.impl

import aws.smithy.kotlin.runtime.retries.delay.AdaptiveRateLimiter
import aws.smithy.kotlin.runtime.retries.delay.AdaptiveRateMeasurer
import aws.smithy.kotlin.runtime.retries.delay.CubicRateCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private const val TOLERANCE = 0.0000005

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveRetryIntegrationTest {
    @Test
    fun testCubicCases() = runTest {
        val timeSource = testTimeSource
        val testCases = adaptiveRetryCubicTestCases.deserializeYaml(CubicTestCase.serializer())

        testCases.forEach { (name, tc) ->
            val start = timeSource.markNow()
            val tsSecondsToTimeMark = { timestamp: Double -> start + timestamp.seconds }

            val rateCalculator = CubicRateCalculator(
                AdaptiveRateLimiter.Config.Default,
                timeSource,
                lastMaxRate = tc.given.lastMaxRate,
                lastThrottleTime = tsSecondsToTimeMark(tc.given.lastThrottleTimeSeconds),
            )

            var lastCalculatedRate = tc.given.lastMaxRate
            tc.cases.forEachIndexed { idx, case ->
                val caseTimeMark = tsSecondsToTimeMark(case.tsSeconds)
                val delayDuration = caseTimeMark - timeSource.markNow()
                delay(delayDuration)

                lastCalculatedRate = when (case.response) {
                    ResponseType.Success -> {
                        rateCalculator.timeWindow = rateCalculator.calculateTimeWindow()
                        rateCalculator.cubicSuccess()
                    }
                    ResponseType.Throttle -> {
                        rateCalculator.lastMaxRate = lastCalculatedRate
                        rateCalculator.timeWindow = rateCalculator.calculateTimeWindow()
                        rateCalculator.lastThrottleTime = tsSecondsToTimeMark(case.tsSeconds)
                        rateCalculator.cubicThrottle(lastCalculatedRate)
                    }
                }

                assertEquals(
                    case.calculatedRate,
                    lastCalculatedRate,
                    TOLERANCE,
                    "Expected matching calculated rate for test $name, index $idx",
                )
            }
        }
    }

    @Test
    fun testE2eCases() = runTest {
        val timeSource = testTimeSource
        val testCases = adaptiveRetryE2eTestCases.deserializeYaml(E2eTestCase.serializer())
        val config = AdaptiveRateLimiter.Config.Default
        val rateMeasurer = AdaptiveRateMeasurer(config, timeSource)
        val rateCalculator = CubicRateCalculator(config, timeSource)
        val rateLimiter = AdaptiveRateLimiter(config, timeSource, rateMeasurer, rateCalculator)

        testCases.forEach { (name, tc) ->
            val start = timeSource.markNow()
            val tsSecondsToTimeMark = { timestamp: Double -> start + timestamp.seconds }

            tc.cases.forEachIndexed { idx, case ->
                val caseTimeMark = tsSecondsToTimeMark(case.tsSeconds)
                val delayDuration = caseTimeMark - timeSource.markNow()
                delay(delayDuration)

                rateLimiter.update(case.response.errorType)

                assertEquals(
                    case.newTokenBucketRate,
                    rateLimiter.refillUnitsPerSecond,
                    TOLERANCE,
                    "Expected matching refill rate for test $name, index $idx",
                )

                assertEquals(
                    case.measuredTxRate,
                    rateMeasurer.measuredTxRate,
                    TOLERANCE,
                    "Expected matching refill rate for test $name, index $idx",
                )
            }
        }
    }
}
