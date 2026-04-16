/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

public actual typealias IOException = java.io.IOException

public actual open class EOFException actual constructor(
    message: String?,
    cause: Throwable?,
) : java.io.EOFException(message) {
    init {
        initCause(cause)
    }

    public actual constructor() : this(null, null)
    public actual constructor(message: String?) : this(message, null)
}
