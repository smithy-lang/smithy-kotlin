/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.telemetry.logging.crt.CrtLoggerProvider

internal actual val DefaultLoggerProvider: LoggerProvider = CrtLoggerProvider()
