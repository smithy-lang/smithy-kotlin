/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.SdkSource

public interface Codec {
    public fun createSerializer(sink: SdkSink): Serializer
    public fun createDeserializer(source: SdkSource): Deserializer
}
