/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

public expect class GzipByteReadChannel(channel: SdkByteReadChannel) : SdkByteReadChannel
