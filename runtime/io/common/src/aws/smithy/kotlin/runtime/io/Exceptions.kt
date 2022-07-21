/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

/**
 * Exception thrown when a content-mutation method such as `write` is invoked upon a read-only buffer.
 */
public class ReadOnlyBufferException : UnsupportedOperationException {
    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)
}

/**
 * Exception thrown when a content-mutation method such as `write` is invoked upon a buffer that cannot grow
 */
public class FixedBufferSizeException : UnsupportedOperationException {
    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)
}
