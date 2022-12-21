/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.io.SharedCloseable
import aws.smithy.kotlin.runtime.util.InternalApi

private class BorrowedHttpClientEngine(
    private val borrowed: HttpClientEngine,
) : HttpClientEngine by borrowed {
    override fun share() { }

    override fun close() { }
}

/**
 * Wraps an externally-owned [HttpClientEngine] with a no-op [SharedCloseable] implementation.
 */
@InternalApi
public fun HttpClientEngine.borrow(): HttpClientEngine =
    if (this is BorrowedHttpClientEngine) this else BorrowedHttpClientEngine(this)
