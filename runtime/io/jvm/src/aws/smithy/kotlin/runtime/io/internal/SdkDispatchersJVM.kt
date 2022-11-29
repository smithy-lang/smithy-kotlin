/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@InternalApi
public actual object SdkDispatchers {
    public actual val IO: CoroutineDispatcher = Dispatchers.IO
}
