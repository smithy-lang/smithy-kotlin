/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.impl

import tools.jackson.databind.EnumNamingStrategies
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

val yamlMapper: YAMLMapper = run {
    val kotlinModule = KotlinModule.Builder().build()
    YAMLMapper
        .builder()
        .addModule(kotlinModule)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enumNamingStrategy(EnumNamingStrategies.SNAKE_CASE)
        .build()
}

inline fun <reified T> Map<String, String>.deserializeYaml(): Map<String, T> = mapValues { (_, value) ->
    yamlMapper.readValue<T>(value)
}
