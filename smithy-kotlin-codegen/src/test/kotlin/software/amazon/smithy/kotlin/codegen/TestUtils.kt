/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model

/**
 * Load a model from the resources directory
 */
fun loadModelFromResource(
    modelName: String,
    path: String = "software/amazon/smithy/kotlin/codegen"
): Model {
    return object {}.javaClass
        .classLoader
        .getResource("$path/$modelName")!!
        .toSmithyModel()
}
