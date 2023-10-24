/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.context

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@InternalApi
public actual class TelemetryContextElement public actual constructor(
    public actual val context: Context,
) : CoroutineContext.Element, AbstractCoroutineContextElement(TelemetryContextElement) {
    public actual companion object Key : CoroutineContext.Key<TelemetryContextElement>
}
