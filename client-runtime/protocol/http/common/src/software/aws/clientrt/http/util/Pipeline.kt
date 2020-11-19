/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import io.ktor.util.pipeline.PipelinePhase
import io.ktor.util.pipeline.Pipeline as KtorPipeline

typealias Phase = PipelinePhase
typealias Pipeline<TSubject, TContext> = KtorPipeline<TSubject, TContext>
