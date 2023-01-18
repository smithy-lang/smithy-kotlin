/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.internal

import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.io.SdkManagedCloseable
import aws.smithy.kotlin.runtime.util.InternalApi

private class ManagedHttpClientEngine(
    private val delegate: HttpClientEngine,
) : SdkManagedCloseable(delegate), HttpClientEngine by delegate

/**
 * Wraps an [HttpClientEngine] for internal runtime management by the SDK.
 */
@InternalApi
public fun HttpClientEngine.manage(): HttpClientEngine =
    if (this is ManagedHttpClientEngine) this else ManagedHttpClientEngine(this)
