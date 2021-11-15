/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

/**
 * A component that can configure itself for an operation. This allows the
 * component to tap into whichever part of the request or response lifecycle phase it needs to
 */
interface AutoInstall<I, O> {
    /**
     * Install the component/middleware into the [SdkHttpOperation]. This allows the feature to wire itself up
     * to the underlying operation (e.g. install interceptors for various phases of execution, etc).
     */
    fun install(op: SdkHttpOperation<I, O>)
}
