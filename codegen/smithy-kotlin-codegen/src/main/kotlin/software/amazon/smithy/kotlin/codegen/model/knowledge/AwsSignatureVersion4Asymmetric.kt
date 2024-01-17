/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.model.shapes.ServiceShape

/**
 * AWS Signature Version 4 Asymmetric signing utils
 */
object AwsSignatureVersion4Asymmetric {
    /**
     * Get the SigV4ATrait auth name to sign request for
     *
     * @param serviceShape service shape for the API
     * @return the service name to use in the credential scope to sign for
     */
    fun signingServiceName(serviceShape: ServiceShape): String {
        val sigv4aTrait = serviceShape.expectTrait<SigV4ATrait>()
        return sigv4aTrait.name
    }
}
