/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.benchmarks.serde

import kotlinx.benchmark.*

@BenchmarkMode(Mode.AverageTime)
@Measurement(time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@Warmup(time = 1, timeUnit = BenchmarkTimeUnit.SECONDS)
@State(Scope.Benchmark)
abstract class BenchmarkBase
