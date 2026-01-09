/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen

import aws.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model

/**
 * Load a model from the resources directory
 */
fun loadModelFromResource(
    modelName: String,
    path: String = "aws/smithy/kotlin/codegen",
): Model = object {}
    .javaClass
    .classLoader
    .getResource("$path/$modelName")!!
    .toSmithyModel()
