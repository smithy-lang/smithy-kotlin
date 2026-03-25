/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.codecs

import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource

public interface Codec {
    public fun createDecoder(source: SdkBufferedSource): Decoder
    public fun createEncoder(sink: SdkBufferedSink): Encoder
}
