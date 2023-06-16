/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@InternalApi
public actual class TelemetryContextElement public actual constructor(
    public actual val context: Context,
) : CoroutineContext.Element, ThreadContextElement<Scope>, AbstractCoroutineContextElement(TelemetryContextElement) {

    public actual companion object Key : CoroutineContext.Key<TelemetryContextElement>

    override fun updateThreadContext(context: CoroutineContext): Scope =
        this.context.makeCurrent()

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        oldState.close()
    }
}
