/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import com.test.model.*

/**
 * This is a stub that produces a series of responses.
 * @param resultList The list of responses to provide for each sequential call to the [getEntity] method.
 */
class DefaultWaitersTestClient<T>(resultList: List<Result<T>>) : WaitersTestClient {
    override val config: WaitersTestClient.Config
        get() = error("Unneeded for test")

    override fun close() { }

    private val results = resultList.iterator()

    override suspend fun getPrimitive(input: GetPrimitiveRequest): GetPrimitiveResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetPrimitiveResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getBooleanLogic(input: GetBooleanLogicRequest): GetBooleanLogicResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetBooleanLogicResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getListOperation(input: GetListOperationRequest): GetListOperationResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetListOperationResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getStringEquals(input: GetStringEqualsRequest): GetStringEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetStringEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getMultiSelectList(input: GetMultiSelectListRequest): GetMultiSelectListResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetMultiSelectListResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getMultiSelectHash(input: GetMultiSelectHashRequest): GetMultiSelectHashResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetMultiSelectHashResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionContains(input: GetFunctionContainsRequest): GetFunctionContainsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionContainsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionLength(input: GetFunctionLengthRequest): GetFunctionLengthResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionLengthResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionAbs(input: GetFunctionAbsRequest): GetFunctionAbsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionAbsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionFloor(input: GetFunctionFloorRequest): GetFunctionFloorResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionFloorResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionCeil(input: GetFunctionCeilRequest): GetFunctionCeilResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionCeilResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getSubFieldProjection(input: GetSubFieldProjectionRequest): GetSubFieldProjectionResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetSubFieldProjectionResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionSumEquals(input: GetFunctionSumEqualsRequest): GetFunctionSumEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionSumEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionAvgEquals(input: GetFunctionAvgEqualsRequest): GetFunctionAvgEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionAvgEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionJoinEquals(input: GetFunctionJoinEqualsRequest): GetFunctionJoinEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionJoinEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionStartsWithEquals(input: GetFunctionStartsWithEqualsRequest): GetFunctionStartsWithEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionStartsWithEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }

    override suspend fun getFunctionEndsWithEquals(input: GetFunctionEndsWithEqualsRequest): GetFunctionEndsWithEqualsResponse {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<GetFunctionEndsWithEqualsResponse>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }
}
