/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

/**
 * Writes an EMF JSON document to stdout.
 * In Lambda/ECS, stdout is automatically ingested by CloudWatch Logs.
 */
internal fun emfLog(message: String) {
    println(message)
}
