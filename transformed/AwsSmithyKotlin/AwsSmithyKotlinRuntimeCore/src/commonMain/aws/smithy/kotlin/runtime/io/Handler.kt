/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

/**
 * Handler is an (asynchronous) transform from [Request] -> [Response]
 */
public interface Handler<in Request, out Response> {
    public suspend fun call(request: Request): Response
}
