/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.context.Scope
import io.opentelemetry.context.Context as OpenTelemetryContext
import io.opentelemetry.context.Scope as OtelScope

internal object OtelContextManager : ContextManager {
    override fun current(): Context =
        OtelContext(OpenTelemetryContext.current())
}

internal class OtelContext(
    val context: OpenTelemetryContext,
) : Context {
    override fun makeCurrent(): Scope {
        val otelScope = context.makeCurrent()
        return OtelScopeImpl(otelScope)
    }
}

private class OtelScopeImpl(private val otelScope: OtelScope) : Scope {
    override fun close() {
        otelScope.close()
    }
}
