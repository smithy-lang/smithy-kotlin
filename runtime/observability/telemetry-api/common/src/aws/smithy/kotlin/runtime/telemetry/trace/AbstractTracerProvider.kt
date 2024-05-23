/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.trace

/**
 * An abstract implementation of a tracer provider. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractTracerProvider : TracerProvider {
    override fun getOrCreateTracer(scope: String): Tracer = Tracer.None
}
