/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awssigning.mergeInto
import aws.smithy.kotlin.runtime.client.endpoints.SigningContext
import aws.smithy.kotlin.runtime.util.merge
import aws.smithy.kotlin.runtime.util.mutableAttributes
import aws.smithy.kotlin.runtime.util.toMutableAttributes

/**
 * Merge the list of modeled auth options with the auth schemes from the resolved endpoint context.
 */
@InternalApi
public fun mergeAuthOptions(modeled: List<AuthOption>, endpointOptions: List<AuthOption>): List<AuthOption> {
    // merge the two lists, preferring the priority order from endpoints
    val modeledById = modeled.associateBy(AuthOption::schemeId)
    val merged = mutableListOf<AuthOption>()
    endpointOptions.forEach {
        val modeledOption = modeledById[it.schemeId]
        val option = if (modeledOption != null && !modeledOption.attributes.isEmpty) {
            val attrs = modeledOption.attributes.toMutableAttributes()
            attrs.merge(it.attributes)
            AuthOption(it.schemeId, attrs)
        } else {
            it
        }

        merged.add(option)
    }

    // tack on auth options that only exist in modeled list
    val mergedById = merged.associateBy(AuthOption::schemeId)
    val modelOnlyOptions = modeled.filterNot { mergedById.contains(it.schemeId) }
    merged.addAll(modelOnlyOptions)

    return merged
}

@InternalApi
public fun SigningContext.toAuthOption(): AuthOption {
    val attrs = mutableAttributes()
    mergeInto(attrs)
    return when (this) {
        is SigningContext.SigV4 -> AuthOption(AuthSchemeId.AwsSigV4, attrs)
        is SigningContext.SigV4A -> AuthOption(AuthSchemeId.AwsSigV4Asymmetric, attrs)
    }
}
