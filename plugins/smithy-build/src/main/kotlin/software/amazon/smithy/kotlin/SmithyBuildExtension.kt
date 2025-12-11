/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import software.amazon.smithy.gradle.SmithyUtils
import software.amazon.smithy.kotlin.dsl.SmithyProjection
import java.nio.file.Path

/**
 * Register and build Smithy projections
 */
open class SmithyBuildExtension(private val project: Project) {

    val projections = project.objects.domainObjectContainer(SmithyProjection::class.java) { name ->
        SmithyProjection(name)
    }

    /**
     * Get the output projection path for the given projection and plugin name
     *
     * @param projectionName the name of the projection to get the output path for
     * @param pluginName the name of the plugin to get the output path for
     */
    public fun getProjectionPath(projectionName: String, pluginName: String): Provider<Path> = SmithyUtils.getProjectionOutputDirProperty(project).map {
        SmithyUtils.getProjectionPluginPath(it.asFile, projectionName, pluginName)
    }
}

// smithy-kotlin specific extensions

/**
 * Get the projection path for the given projection name for the `smithy-kotlin` plugin.
 * This is equivalent to `smithyBuild.getProjectionPath(projectionName, "kotlin-codegen")`
 *
 * @param projectionName the name of the projection to use
 */
public fun SmithyBuildExtension.smithyKotlinProjectionPath(projectionName: String): Provider<Path> = getProjectionPath(projectionName, "kotlin-codegen")

/**
 * Get the default generated kotlin source directory for the `smithy-kotlin` plugin.
 * This is equivalent to `smithyBuild.getProjectionPath(projectionName, "kotlin-codegen")`
 *
 * @param projectionName the name of the projection to use
 */
public fun SmithyBuildExtension.smithyKotlinProjectionSrcDir(projectionName: String): Provider<Path> = smithyKotlinProjectionPath(projectionName).map { it.resolve("src/main/kotlin") }
