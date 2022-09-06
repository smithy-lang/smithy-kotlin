/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.utils

import java.util.*

/**
 * Get the value if present otherwise return null
 */
fun <T> Optional<T>.getOrNull(): T? = orElse(null)
