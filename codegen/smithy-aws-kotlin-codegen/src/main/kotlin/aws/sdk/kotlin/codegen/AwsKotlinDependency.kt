/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.sdk.kotlin.codegen

import software.amazon.smithy.kotlin.codegen.GradleConfiguration
import software.amazon.smithy.kotlin.codegen.KotlinDependency

// root namespace for the AWS client-runtime
const val AWS_CLIENT_RT_ROOT_NS = "aws.sdk.kotlin.runtime"
const val AWS_CLIENT_RT_AUTH_NS = "aws.sdk.kotlin.runtime.auth"
const val AWS_CLIENT_RT_REGIONS_NS = "aws.sdk.kotlin.runtime.regions"

// publishing info
const val AWS_CLIENT_RT_GROUP = "aws.sdk.kotlin.runtime"
const val AWS_CLIENT_RT_VERSION = "0.0.1"

/**
 * Container object for AWS specific dependencies
 */
object AwsKotlinDependency {
    val AWS_CLIENT_RT_CORE = KotlinDependency(GradleConfiguration.Api, AWS_CLIENT_RT_ROOT_NS, AWS_CLIENT_RT_GROUP, "aws-client-rt", AWS_CLIENT_RT_VERSION)
    val AWS_CLIENT_RT_HTTP = KotlinDependency(GradleConfiguration.Api, "$AWS_CLIENT_RT_ROOT_NS.http", AWS_CLIENT_RT_GROUP, "http", AWS_CLIENT_RT_VERSION)
    val AWS_CLIENT_RT_AUTH = KotlinDependency(GradleConfiguration.Api, AWS_CLIENT_RT_AUTH_NS, AWS_CLIENT_RT_GROUP, "auth", AWS_CLIENT_RT_VERSION)
    val AWS_CLIENT_RT_REGIONS = KotlinDependency(GradleConfiguration.Api, AWS_CLIENT_RT_REGIONS_NS, AWS_CLIENT_RT_GROUP, "regions", AWS_CLIENT_RT_VERSION)
    val REST_JSON_FEAT = KotlinDependency(GradleConfiguration.Implementation, "$AWS_CLIENT_RT_ROOT_NS.restjson", AWS_CLIENT_RT_GROUP, "rest-json", AWS_CLIENT_RT_VERSION)
}
