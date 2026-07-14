/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.Headers as SdkHeaders
import okhttp3.Headers as OkHttpHeaders

/**
 * Proxy [okhttp3.Headers] as [aws.smithy.kotlin.runtime.http.Headers]
 */
@InternalApi
public class OkHttpHeadersAdapter(private val headers: OkHttpHeaders) : SdkHeaders {
    override val caseInsensitiveName: Boolean = true

    private val multimap: Map<String, List<String>> = headers.toMultimap()

    override fun getAll(name: String): List<String>? = multimap[name]

    override fun names(): Set<String> = multimap.keys

    override fun entries(): Set<Map.Entry<String, List<String>>> = multimap.entries

    override fun contains(name: String): Boolean = name in multimap

    override fun isEmpty(): Boolean = headers.size == 0
}
