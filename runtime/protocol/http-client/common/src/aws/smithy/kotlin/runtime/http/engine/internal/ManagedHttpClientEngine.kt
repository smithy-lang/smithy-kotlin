/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.engine.CloseableHttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.io.SdkManagedCloseable

private class ManagedHttpClientEngine(
    private val delegate: CloseableHttpClientEngine,
) : SdkManagedCloseable(delegate), CloseableHttpClientEngine by delegate

/**
 * Wraps an [HttpClientEngine] for internal runtime management by the SDK if possible.
 */
@InternalApi
public fun HttpClientEngine.manage(): HttpClientEngine = when (this) {
    is ManagedHttpClientEngine -> this
    is CloseableHttpClientEngine -> ManagedHttpClientEngine(this)
    else -> this
}
