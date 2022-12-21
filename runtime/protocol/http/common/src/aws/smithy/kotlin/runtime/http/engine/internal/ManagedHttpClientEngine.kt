/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.io.SharedCloseable
import aws.smithy.kotlin.runtime.io.SharedCloseableImpl
import aws.smithy.kotlin.runtime.util.InternalApi

private class ManagedHttpClientEngine(
    private val managed: HttpClientEngine,
) : HttpClientEngine by managed {
    private val wrapped = SharedCloseableImpl(managed)

    override fun share() { wrapped.share() }

    override fun close() { wrapped.close() }
}

/**
 * Wraps an [HttpClientEngine] to implement [SharedCloseable] for tracking internal use across multiple clients.
 */
@InternalApi
public fun HttpClientEngine.manage(): HttpClientEngine =
    if (this is ManagedHttpClientEngine) this else ManagedHttpClientEngine(this)

/**
 * Extension to check whether an [HttpClientEngine] is managed.
 */
@InternalApi
public fun HttpClientEngine.isManaged(): Boolean = this is ManagedHttpClientEngine
