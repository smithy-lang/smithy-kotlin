/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// FIXME Move to jvmAndNative when https://github.com/Kotlin/kotlinx.coroutines/issues/3326 is implemented
@InternalApi
public actual class TelemetryContextElement public actual constructor(
    public actual val context: Context,
) : AbstractCoroutineContextElement(TelemetryContextElement),
    CoroutineContext.Element,
    ThreadContextElement<Scope> {

    public actual companion object Key : CoroutineContext.Key<TelemetryContextElement>

    override fun updateThreadContext(context: CoroutineContext): Scope = this.context.makeCurrent()

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        oldState.close()
    }
}
