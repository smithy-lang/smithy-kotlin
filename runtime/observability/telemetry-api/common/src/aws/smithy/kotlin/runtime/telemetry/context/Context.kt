/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

/**
 * Context is an opaque propagation mechanism for telemetry providers to carry execution
 * scoped values across API boundaries.
 */
public interface Context {
    public companion object {
        /**
         * A no-op [Context]
         */
        public val None: Context = NoOpContext
    }

    /**
     * Make this the currently active context
     *
     * @return handle to a [Scope] that MUST be disposed of (via [Scope.close]) when the current context
     * is considered no longer active (returning the current active context to whatever it was previously if set).
     */
    public fun makeCurrent(): Scope
}

private object NoOpContext : Context {
    override fun makeCurrent(): Scope = NoOpScope
}

private object NoOpScope : Scope {
    override fun close() {}
}
