/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import software.amazon.smithy.kotlin.dsl.SmithyProjection
import software.amazon.smithy.kotlin.dsl.withObjectMember
import software.amazon.smithy.model.node.Node

private const val SMITHY_BUILD_CONFIG_FILENAME = "smithy-build.json"

/**
 * Task that generates `smithy-build.json` from a set of projections
 */
abstract class GenerateSmithyBuild : DefaultTask() {

    /**
     * The projections to generate as JSON string
     */
    @get:Input
    public abstract val smithyBuildConfig: Property<String>

    /**
     * The generated `smithy-build.json` configuration file.
     * Defaults to the project build directory.
     */
    @get:OutputFile
    public abstract val generatedOutput: RegularFileProperty

    init {
        generatedOutput.convention(
            project.layout.buildDirectory.file(SMITHY_BUILD_CONFIG_FILENAME),
        )
    }

    /**
     * Generate `smithy-build.json`
     */
    @TaskAction
    fun generateSmithyBuild() {
        val buildConfig = generatedOutput.get().asFile
        if (buildConfig.exists()) {
            buildConfig.delete()
        }

        buildConfig.parentFile.mkdirs()
        buildConfig.writeText(smithyBuildConfig.get())
    }
}

internal val Collection<SmithyProjection>.json: String
    get() = projectionsToBuildConfig(this)

internal fun projectionsToBuildConfig(projections: Collection<SmithyProjection>): String {
    val buildConfig = Node.objectNodeBuilder()
        .withMember("version", "1.0")
        .withObjectMember("projections") {
            projections.forEach { projection ->
                withMember(projection.name, projection.toNode())
            }
        }
        .build()

    return Node.prettyPrintJson(buildConfig)
}
