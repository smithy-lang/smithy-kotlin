/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

public actual open class IOException actual constructor(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    public actual constructor() : this(null)
    public actual constructor(message: String?) : this(message, null)
}

public actual open class EOFException actual constructor(message: String?) : IOException(message) {
    public actual constructor() : this(null)
}
