/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.GetEntityRequest
import com.test.model.GetEntityResponse
import kotlin.Result

/**
 * This is a stub that produces a series of responses.
 * @param resultList The list of responses to provide for each sequential call to the [getEntity] method.
 */
class DefaultWaitersTestClient(resultList: List<Result<GetEntityResponse>>) : WaitersTestClient {
    override val config: WaitersTestClient.Config
        get() = error("Unneeded for test")

    override fun close() { }

    private val results = resultList.iterator()

    override suspend fun getEntity(input: GetEntityRequest): GetEntityResponse {
        val nextResult = results.next()
        return if (nextResult.isSuccess) nextResult.getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }
}
