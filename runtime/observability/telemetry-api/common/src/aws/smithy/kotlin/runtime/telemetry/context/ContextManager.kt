/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

/**
 * Responsible for managing the current context with callers current execution unit. For example, some implementations
 * use Thread Local storage for managing the current context.
 */
public interface ContextManager {
    public companion object {
        public val None: ContextManager = NoOpContextManager
    }

    /**
     * Return the current [Context]
     */
    public fun current(): Context
}

private object NoOpContextManager : ContextManager {
    override fun current(): Context = Context.None
}
