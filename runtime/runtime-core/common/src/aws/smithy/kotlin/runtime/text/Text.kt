/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.text

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public fun String.ensurePrefix(prefix: String): String = if (startsWith(prefix)) this else prefix + this

@InternalApi
public fun String.ensureSuffix(suffix: String): String = if (endsWith(suffix)) this else plus(suffix)
