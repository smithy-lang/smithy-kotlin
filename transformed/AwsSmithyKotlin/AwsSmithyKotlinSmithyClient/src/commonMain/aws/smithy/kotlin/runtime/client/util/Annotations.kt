/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client.util

/**
 * A KMP-compatible variant of [kotlin.jvm.JvmDefaultWithoutCompatibility]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public expect annotation class MpJvmDefaultWithoutCompatibility()
