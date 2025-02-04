/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging.crt

import aws.sdk.kotlin.crt.Config as CrtConfig
import aws.smithy.kotlin.runtime.telemetry.logging.*

public class CrtLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger = CrtLogger(name, CrtConfig())
    public fun getOrCreateLogger(name: String, config: CrtConfig): Logger = CrtLogger(name, config)
}

