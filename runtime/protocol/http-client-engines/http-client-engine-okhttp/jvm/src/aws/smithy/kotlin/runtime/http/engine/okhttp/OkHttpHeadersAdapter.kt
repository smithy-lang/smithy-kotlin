/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.Headers as SdkHeaders
import okhttp3.Headers as OkHttpHeaders

/**
 * Proxy [okhttp3.Headers] as [aws.smithy.kotlin.runtime.http.Headers]
 */
internal class OkHttpHeadersAdapter(private val headers: OkHttpHeaders) : SdkHeaders {
    override val caseInsensitiveName: Boolean = true

    override fun getAll(name: String): List<String>? =
        headers.values(name).ifEmpty { null }

    override fun names(): Set<String> = headers.names()

    override fun entries(): Set<Map.Entry<String, List<String>>> =
        headers.toMultimap().entries

    override fun contains(name: String): Boolean =
        headers[name] != null

    override fun isEmpty(): Boolean = headers.size == 0
}
