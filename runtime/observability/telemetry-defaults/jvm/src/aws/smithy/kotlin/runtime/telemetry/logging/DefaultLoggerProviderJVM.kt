/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.telemetry.logging.slf4j.Slf4jLoggerProvider

internal actual val DefaultLoggerProvider: LoggerProvider = Slf4jLoggerProvider
