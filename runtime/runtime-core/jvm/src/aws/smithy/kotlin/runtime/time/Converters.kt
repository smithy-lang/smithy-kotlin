/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.time

/**
 * Converts this [aws.smithy.kotlin.runtime.time.Instant] to a [java.time.Instant].
 */
public fun Instant.toJvmInstant(): java.time.Instant = value

/**
 * Converts this [java.time.Instant] to a [aws.smithy.kotlin.runtime.time.Instant].
 */
public fun java.time.Instant.toSdkInstant(): Instant = Instant(this)
