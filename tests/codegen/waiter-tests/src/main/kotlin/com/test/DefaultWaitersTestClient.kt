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

    override suspend fun getPrimitive(input: GetPrimitiveRequest): GetPrimitiveResponse = findSuccess()

    override suspend fun getBooleanLogic(input: GetBooleanLogicRequest): GetBooleanLogicResponse = findSuccess()

    override suspend fun getListOperation(input: GetListOperationRequest): GetListOperationResponse = findSuccess()

    override suspend fun getStringEquals(input: GetStringEqualsRequest): GetStringEqualsResponse = findSuccess()

    override suspend fun getMultiSelectList(input: GetMultiSelectListRequest): GetMultiSelectListResponse = findSuccess()

    override suspend fun getMultiSelectHash(input: GetMultiSelectHashRequest): GetMultiSelectHashResponse = findSuccess()

    override suspend fun getFunctionContains(input: GetFunctionContainsRequest): GetFunctionContainsResponse = findSuccess()

    override suspend fun getFunctionLength(input: GetFunctionLengthRequest): GetFunctionLengthResponse = findSuccess()

    override suspend fun getFunctionAbs(input: GetFunctionAbsRequest): GetFunctionAbsResponse = findSuccess()

    override suspend fun getFunctionFloor(input: GetFunctionFloorRequest): GetFunctionFloorResponse = findSuccess()

    override suspend fun getFunctionCeil(input: GetFunctionCeilRequest): GetFunctionCeilResponse = findSuccess()

    override suspend fun getSubFieldProjection(input: GetSubFieldProjectionRequest): GetSubFieldProjectionResponse = findSuccess()

    override suspend fun getFunctionSumEquals(input: GetFunctionSumEqualsRequest): GetFunctionSumEqualsResponse = findSuccess()

    override suspend fun getFunctionAvgEquals(input: GetFunctionAvgEqualsRequest): GetFunctionAvgEqualsResponse = findSuccess()

    override suspend fun getFunctionJoinEquals(input: GetFunctionJoinEqualsRequest): GetFunctionJoinEqualsResponse = findSuccess()

    override suspend fun getFunctionStartsWithEquals(input: GetFunctionStartsWithEqualsRequest): GetFunctionStartsWithEqualsResponse = findSuccess()

    override suspend fun getFunctionEndsWithEquals(input: GetFunctionEndsWithEqualsRequest): GetFunctionEndsWithEqualsResponse = findSuccess()

    override suspend fun getFunctionKeysEquals(input: GetFunctionKeysEqualsRequest): GetFunctionKeysEqualsResponse = findSuccess()

    override suspend fun getFunctionValuesEquals(input: GetFunctionValuesEqualsRequest): GetFunctionValuesEqualsResponse = findSuccess()

    override suspend fun getFunctionMergeEquals(input: GetFunctionMergeEqualsRequest): GetFunctionMergeEqualsResponse = findSuccess()

    override suspend fun getFunctionMaxEquals(input: GetFunctionMaxEqualsRequest): GetFunctionMaxEqualsResponse = findSuccess()

    override suspend fun getFunctionMinEquals(input: GetFunctionMinEqualsRequest): GetFunctionMinEqualsResponse = findSuccess()

    override suspend fun getFunctionReverseEquals(input: GetFunctionReverseEqualsRequest): GetFunctionReverseEqualsResponse = findSuccess()

    override suspend fun getFunctionNotNullEquals(input: GetFunctionNotNullEqualsRequest): GetFunctionNotNullEqualsResponse = findSuccess()

    override suspend fun getFunctionToArrayEquals(input: GetFunctionToArrayEqualsRequest): GetFunctionToArrayEqualsResponse = findSuccess()

    override suspend fun getFunctionToStringEquals(input: GetFunctionToStringEqualsRequest): GetFunctionToStringEqualsResponse = findSuccess()

    override suspend fun getFunctionToNumberEquals(input: GetFunctionToNumberEqualsRequest): GetFunctionToNumberEqualsResponse = findSuccess()

    override suspend fun getFunctionTypeEquals(input: GetFunctionTypeEqualsRequest): GetFunctionTypeEqualsResponse = findSuccess()

    private fun <Response> findSuccess(): Response {
        val nextResult = results.next()
        @Suppress("UNCHECKED_CAST")
        return if (nextResult.isSuccess) (nextResult as Result<Response>).getOrNull()!! else throw nextResult.exceptionOrNull()!!
    }
}
