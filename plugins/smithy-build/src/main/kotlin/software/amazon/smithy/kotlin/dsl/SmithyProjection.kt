/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.dsl

import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode

/**
 * A container for settings related to a single Smithy projection.
 *
 * See https://awslabs.github.io/smithy/1.0/guides/building-models/build-config.html#projections
 *
 * @param name the name of the projection
 */
class SmithyProjection(val name: String) {

    /**
     * List of files/directories that contain models that are considered sources models of the build.
     */
    var sources: List<String> = emptyList()

    /**
     * List of files/directories to import when building the projection
     */
    var imports: List<String> = emptyList()

    /**
     * A list of transforms to apply
     *
     * See https://awslabs.github.io/smithy/1.0/guides/building-models/build-config.html#transforms
     */
    var transforms: List<String> = emptyList()

    /**
     * Plugin name to plugin settings. Plugins should provide an extension function to configure their own plugin settings
     */
    val plugins: MutableMap<String, SmithyBuildPluginSettings> = mutableMapOf()

    internal fun toNode(): Node {
        // escape windows paths for valid json
        val formattedImports = imports.map { it.replace("\\", "\\\\") }
        val formattedSources = sources.map { it.replace("\\", "\\\\") }

        val transformNodes = transforms.map { Node.parse(it) }
        val obj = ObjectNode.objectNodeBuilder()
            .withArrayMember("sources", formattedSources)
            .withArrayMember("imports", formattedImports)
            .withMember("transforms", ArrayNode.fromNodes(transformNodes))

        if (plugins.isNotEmpty()) {
            obj.withObjectMember("plugins") {
                plugins.forEach { (pluginName, pluginSettings) ->
                    withMember(pluginName, pluginSettings.toNode())
                }
            }
        }
        return obj.build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmithyProjection

        if (name != other.name) return false
        if (sources != other.sources) return false
        if (imports != other.imports) return false
        if (transforms != other.transforms) return false
        if (plugins != other.plugins) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + imports.hashCode()
        result = 31 * result + sources.hashCode()
        result = 31 * result + transforms.hashCode()
        result = 31 * result + plugins.hashCode()
        return result
    }
}
