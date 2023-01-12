/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.io.ManagedCloseable
import aws.smithy.kotlin.runtime.util.InternalApi

@InternalApi
public class ManagedHttpClientEngine(
    private val delegate: HttpClientEngine,
) : ManagedCloseable(delegate), HttpClientEngine by delegate {
    public override fun close() { super<ManagedCloseable>.close() }
}

/**
 * Wraps an [HttpClientEngine] to track shared use across clients.
 */
@InternalApi
public fun HttpClientEngine.manage(): HttpClientEngine =
    if (this is ManagedHttpClientEngine) this else ManagedHttpClientEngine(this)
