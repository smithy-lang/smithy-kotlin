/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.traits.auth

import aws.smithy.kotlin.runtime.auth.AuthOption

object DefaultLambdaAuthSchemeProvider : LambdaAuthSchemeProvider {
    override suspend fun resolveAuthScheme(params: LambdaAuthSchemeParameters): List<AuthOption> {
        error("not needed for paginator integration tests")
    }
}
