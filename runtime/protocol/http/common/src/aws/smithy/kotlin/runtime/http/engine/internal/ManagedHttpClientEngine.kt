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
 * Wraps a [CloseableHttpClientEngine] for internal runtime management by the SDK.
 */
@InternalApi
public fun CloseableHttpClientEngine.manage(): HttpClientEngine =
    if (this is ManagedHttpClientEngine) this else ManagedHttpClientEngine(this)
