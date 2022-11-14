/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

public expect open class IOException(message: String?, cause: Throwable?) : Exception {
    public constructor(message: String? = null)
}

public expect open class EOFException(message: String? = null) : IOException
