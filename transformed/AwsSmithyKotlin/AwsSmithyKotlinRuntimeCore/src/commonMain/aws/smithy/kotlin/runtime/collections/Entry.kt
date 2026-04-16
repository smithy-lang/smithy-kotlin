/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

internal data class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>
