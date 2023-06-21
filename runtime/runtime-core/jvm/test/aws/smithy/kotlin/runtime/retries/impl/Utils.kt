/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.impl

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.DeserializationStrategy

fun <T> Map<String, String>.deserialize(serializer: DeserializationStrategy<T>): Map<String, T> =
    mapValues { Yaml.default.decodeFromString(serializer, it.value) }
