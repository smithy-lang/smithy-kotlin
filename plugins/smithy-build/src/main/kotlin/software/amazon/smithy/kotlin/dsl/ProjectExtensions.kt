/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.dsl

import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import software.amazon.smithy.gradle.tasks.SmithyBuildTask
import software.amazon.smithy.kotlin.tasks.GenerateSmithyBuild

internal const val TASK_GENERATE_SMITHY_BUILD = "generateSmithyBuild"
internal const val TASK_GENERATE_SMITHY_PROJECTIONS = "generateSmithyProjections"

public val TaskContainer.generateSmithyBuild: TaskProvider<GenerateSmithyBuild>
    get() = named<GenerateSmithyBuild>(TASK_GENERATE_SMITHY_BUILD)

public val TaskContainer.generateSmithyProjections: TaskProvider<SmithyBuildTask>
    get() = named<SmithyBuildTask>(TASK_GENERATE_SMITHY_PROJECTIONS)
