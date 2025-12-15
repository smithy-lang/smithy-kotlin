/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.dsl

import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.ToNode
import java.util.*

/**
 * Container for `smithy-kotlin` plugin settings
 * See https://github.com/smithy-lang/smithy-kotlin/blob/main/codegen/smithy-kotlin-codegen/src/main/kotlin/software/amazon/smithy/kotlin/codegen/KotlinSettings.kt
 */
open class SmithyKotlinApiSettings : ToNode {
    var visibility: String? = null
    var nullabilityCheckMode: String? = null
    var defaultValueSerializationMode: String? = null
    var enableEndpointAuthProvider: Boolean? = null

    override fun toNode(): Node {
        val builder = ObjectNode.objectNodeBuilder()
        builder.withNullableMember("visibility", visibility)
        builder.withNullableMember("nullabilityCheckMode", nullabilityCheckMode)
        builder.withNullableMember("defaultValueSerializationMode", defaultValueSerializationMode)
        builder.withNullableMember("enableEndpointAuthProvider", enableEndpointAuthProvider)
        return builder.build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmithyKotlinApiSettings

        if (visibility != other.visibility) return false
        if (nullabilityCheckMode != other.nullabilityCheckMode) return false
        if (defaultValueSerializationMode != other.defaultValueSerializationMode) return false
        if (enableEndpointAuthProvider != other.enableEndpointAuthProvider) return false

        return true
    }

    override fun hashCode(): Int {
        var result = visibility?.hashCode() ?: 0
        result = 31 * result + (nullabilityCheckMode?.hashCode() ?: 0)
        result = 31 * result + (defaultValueSerializationMode?.hashCode() ?: 0)
        result = 31 * result + (enableEndpointAuthProvider?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SmithyKotlinApiSettings(visibility=$visibility, nullabilityCheckMode=$nullabilityCheckMode, defaultValueSerializationMode=$defaultValueSerializationMode, enableEndpointAuthProvider=$enableEndpointAuthProvider)"
}

open class SmithyKotlinBuildSettings : ToNode {
    var generateFullProject: Boolean? = null
    var generateDefaultBuildFiles: Boolean? = null
    var optInAnnotations: List<String>? = null

    override fun toNode(): Node {
        val builder = ObjectNode.objectNodeBuilder()

        builder.withNullableMember("rootProject", generateFullProject)
        builder.withNullableMember("generateDefaultBuildFiles", generateDefaultBuildFiles)

        val optInArrNode = optInAnnotations?.map { Node.from(it) }?.let { ArrayNode.fromNodes(it) }
        builder.withOptionalMember("optInAnnotations", Optional.ofNullable(optInArrNode))

        return builder.build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmithyKotlinBuildSettings

        if (generateFullProject != other.generateFullProject) return false
        if (generateDefaultBuildFiles != other.generateDefaultBuildFiles) return false
        if (optInAnnotations != other.optInAnnotations) return false

        return true
    }

    override fun hashCode(): Int {
        var result = generateFullProject?.hashCode() ?: 0
        result = 31 * result + (generateDefaultBuildFiles?.hashCode() ?: 0)
        result = 31 * result + (optInAnnotations?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SmithyKotlinBuildSettings(generateFullProject=$generateFullProject, generateDefaultBuildFiles=$generateDefaultBuildFiles, optInAnnotations=$optInAnnotations)"
}

open class SmithyKotlinPluginSettings : SmithyBuildPluginSettings {
    override val pluginName: String = "kotlin-codegen"

    var serviceShapeId: String? = null
    var packageName: String? = null
    var packageVersion: String? = null
    var packageDescription: String? = null
    var sdkId: String? = null

    internal var buildSettings: SmithyKotlinBuildSettings? = null
    fun buildSettings(configure: SmithyKotlinBuildSettings.() -> Unit) {
        if (buildSettings == null) buildSettings = SmithyKotlinBuildSettings()
        buildSettings!!.apply(configure)
    }

    internal var apiSettings: SmithyKotlinApiSettings? = null
    fun apiSettings(configure: SmithyKotlinApiSettings.() -> Unit) {
        if (apiSettings == null) apiSettings = SmithyKotlinApiSettings()
        apiSettings!!.apply(configure)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmithyKotlinPluginSettings

        if (serviceShapeId != other.serviceShapeId) return false
        if (packageName != other.packageName) return false
        if (packageVersion != other.packageVersion) return false
        if (packageDescription != other.packageDescription) return false
        if (sdkId != other.sdkId) return false
        if (buildSettings != other.buildSettings) return false
        if (apiSettings != other.apiSettings) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serviceShapeId?.hashCode() ?: 0
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (packageVersion?.hashCode() ?: 0)
        result = 31 * result + (packageDescription?.hashCode() ?: 0)
        result = 31 * result + (sdkId?.hashCode() ?: 0)
        result = 31 * result + (buildSettings?.hashCode() ?: 0)
        result = 31 * result + (apiSettings?.hashCode() ?: 0)
        return result
    }

    override fun toNode(): Node {
        val obj = ObjectNode.objectNodeBuilder()
            .withMember("service", serviceShapeId!!)
            .withObjectMember("package") {
                withMember("name", packageName!!)
                withNullableMember("version", packageVersion)
                withNullableMember("description", packageDescription)
            }
            .withNullableMember("sdkId", sdkId)
            .withNullableMember("build", buildSettings)
            .withNullableMember("api", apiSettings)

        return obj.build()
    }

    override fun toString(): String = "SmithyKotlinPluginSettings(pluginName='$pluginName', serviceShapeId=$serviceShapeId, packageName=$packageName, packageVersion=$packageVersion, packageDescription=$packageDescription, sdkId=$sdkId, buildSettings=$buildSettings, apiSettings=$apiSettings)"
}

fun SmithyProjection.smithyKotlinPlugin(configure: SmithyKotlinPluginSettings.() -> Unit) {
    val p = plugins.computeIfAbsent("kotlin-codegen") { SmithyKotlinPluginSettings() } as SmithyKotlinPluginSettings
    p.apply(configure)
}
