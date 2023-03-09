/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.codegen

import software.amazon.smithy.kotlin.codegen.core.RuntimeTypePackage

/**
 * Commonly used AWS runtime types. Provides a single definition of a runtime symbol such that codegen isn't littered
 * with inline symbol creation which makes refactoring of the runtime more difficult and error-prone.
 */
object AwsRuntimeTypes {
    object Core : RuntimeTypePackage(AwsKotlinDependency.AWS_CORE) {
        val AwsErrorMetadata = symbol("AwsErrorMetadata")
        val ClientException = symbol("ClientException")

        object Client : RuntimeTypePackage(AwsKotlinDependency.AWS_CORE, "client") {
            val AwsClientOption = symbol("AwsClientOption")
            val AwsSdkClientConfig = symbol("AwsSdkClientConfig")
        }
    }

    object Endpoint : RuntimeTypePackage(AwsKotlinDependency.AWS_ENDPOINT) {
        val AuthSchemesAttributeKey = symbol("AuthSchemesAttributeKey")
        val AuthScheme = symbol("AuthScheme")
        val authSchemeEndpointExt = symbol("authScheme")
        val asSigningContextAuthSchemeExt = symbol("asSigningContext")
        val applyToRequestAuthSchemeExt = symbol("applyToRequest")

        object Functions : RuntimeTypePackage(AwsKotlinDependency.AWS_ENDPOINT, "functions") {
            val partitionFn = symbol("partition")
            val Partition = symbol("Partition")
            val PartitionConfig = symbol("PartitionConfig")
            val parseArn = symbol("parseArn")
            val Arn = symbol("Arn")
            val isVirtualHostableS3Bucket = symbol("isVirtualHostableS3Bucket")
        }
    }

    object Config : RuntimeTypePackage(AwsKotlinDependency.AWS_CONFIG) {
        val AbstractAwsSdkClientFactory = symbol("AbstractAwsSdkClientFactory", "config")

        object Profile : RuntimeTypePackage(AwsKotlinDependency.AWS_CONFIG, "config.profile") {
            val AwsProfile = symbol("AwsProfile")
        }

        object Credentials : RuntimeTypePackage(AwsKotlinDependency.AWS_CONFIG, "auth.credentials") {
            val DefaultChainCredentialsProvider = symbol("DefaultChainCredentialsProvider")
            val StaticCredentialsProvider = symbol("StaticCredentialsProvider")
            val manage = symbol("manage", "auth.credentials.internal", isExtension = true)
        }
    }

    object Http : RuntimeTypePackage(AwsKotlinDependency.AWS_HTTP) {
        object Interceptors : RuntimeTypePackage(AwsKotlinDependency.AWS_HTTP, "interceptors") {
            val AddUserAgentMetadataInterceptor = symbol("AddUserAgentMetadataInterceptor")
        }

        object Retries {
            val AwsDefaultRetryPolicy = symbol("AwsDefaultRetryPolicy", "retries")
        }

        object Middleware {
            val AwsRetryHeaderMiddleware = symbol("AwsRetryHeaderMiddleware", "middleware")
        }
    }
}
