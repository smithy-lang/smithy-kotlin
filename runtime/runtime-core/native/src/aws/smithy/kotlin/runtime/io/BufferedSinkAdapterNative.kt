/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

internal actual class BufferedSinkAdapter actual constructor(sink: okio.BufferedSink) : SdkBufferedSink, AbstractBufferedSinkAdapter(sink)