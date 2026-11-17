/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

/**
 * Destination for EMF output. Defaults to stdout, which in Lambda/ECS is automatically ingested by
 * CloudWatch Logs.
 *
 * Overridable within the module so that tests can capture emitted documents directly instead of
 * redirecting the platform's console, which is only expressible on JVM. Reassignment is not
 * synchronized; only tests are expected to write to it, and each must restore the previous value.
 */
internal var emfSink: (String) -> Unit = ::println

/**
 * Writes an EMF JSON document to the configured [emfSink].
 */
internal fun emfLog(message: String) {
    emfSink(message)
}
