/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

/**
 * Runs [block] with EMF output redirected away from stdout, returning the documents emitted in
 * order. Capturing at the sink rather than at the platform console keeps this usable from common
 * code, and lets callers assert on the number of emissions as well as their contents.
 */
internal fun captureEmfOutput(block: () -> Unit): List<String> {
    val captured = mutableListOf<String>()
    val original = emfSink

    emfSink = { captured.add(it) }
    try {
        block()
    } finally {
        emfSink = original
    }

    return captured
}
