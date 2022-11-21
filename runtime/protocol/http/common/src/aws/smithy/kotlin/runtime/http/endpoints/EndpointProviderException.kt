/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.endpoints

import aws.smithy.kotlin.runtime.ClientException

/**
 * Thrown when an EndpointProvider is unable to resolve to an endpoint with the provided parameters.
 */
public class EndpointProviderException(message: String?) : ClientException(message)
