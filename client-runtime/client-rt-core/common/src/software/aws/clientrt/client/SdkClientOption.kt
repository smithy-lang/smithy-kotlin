/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.client

import software.aws.clientrt.config.IdempotencyTokenProvider

/**
 * Common client execution options
 */
public object SdkClientOption {

    /**
     * The name of the operation
     */
    public val OperationName: ClientOption<String> = ClientOption("OperationName")

    /**
     * The service name the operation is executed against
     */
    public val ServiceName: ClientOption<String> = ClientOption("ServiceName")

    /**
     * A token generator for idempotency tokens
     */
    public val IdempotencyTokenProvider: ClientOption<IdempotencyTokenProvider> = ClientOption("IdempotencyTokenProvider")
}
