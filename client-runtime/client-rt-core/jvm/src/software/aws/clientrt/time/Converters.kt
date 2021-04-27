/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.time

/**
 * Converts this [software.aws.clientrt.time.Instant] to a [java.time.Instant].
 */
fun Instant.toJvmInstant(): java.time.Instant = value

/**
 * Converts this [java.time.Instant] to a [software.aws.clientrt.time.Instant].
 */
fun java.time.Instant.toAwsSdkInstant(): Instant = Instant(this)
