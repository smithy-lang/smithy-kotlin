/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.testutils

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

expect class MockByteReadChannel(contents: String, isClosedForRead: Boolean = true, isClosedForWrite: Boolean = true) :
    SdkByteReadChannel
