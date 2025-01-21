/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
internal actual class BufferedSinkAdapter actual constructor(sink: okio.BufferedSink) :
    AbstractBufferedSinkAdapter(sink),
    SdkBufferedSink
