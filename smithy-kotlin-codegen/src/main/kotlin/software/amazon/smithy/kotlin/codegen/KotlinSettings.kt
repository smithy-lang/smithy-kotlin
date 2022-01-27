/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.lang.isValidPackageName
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import java.util.Optional
import java.util.logging.Logger
import kotlin.streams.toList

// shapeId of service from which to generate an SDK
private const val SERVICE = "service"
private const val PACKAGE_SETTINGS = "package"
private const val PACKAGE_NAME = "name"
private const val PACKAGE_VERSION = "version"
private const val PACKAGE_DESCRIPTION = "description"
private const val BUILD_SETTINGS = "build"
// Optional specification of sdkId for models that provide them, otherwise Service's shape id name is used
private const val SDK_ID = "sdkId"

/**
 * Settings used by [KotlinCodegenPlugin]
 */
data class KotlinSettings(
    val service: ShapeId,
    val pkg: PackageSettings,
    val sdkId: String,
    val build: BuildSettings = BuildSettings.Default
) {

    /**
     * Configuration elements specific to the service's package namespace, version, and description.
     */
    data class PackageSettings(val name: String, val version: String, val description: String? = null) {
        /**
         * Derive a subpackage namespace from the root package name
         */
        fun subpackage(subpackageName: String): String = "$name.$subpackageName"
    }

    /**
     * Get the corresponding [ServiceShape] from a model.
     * @return Returns the found `Service`
     * @throws CodegenException if the service is invalid or not found
     */
    fun getService(model: Model): ServiceShape = model
        .getShape(service)
        .orElseThrow { CodegenException("Service shape not found: $service") }
        .asServiceShape()
        .orElseThrow { CodegenException("Shape is not a service: $service") }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(KotlinSettings::class.java.name)

        /**
         * Create settings from a configuration object node.
         *
         * @param model Model to infer the service from (if not explicitly set in config)
         * @param config Config object to load
         * @throws software.amazon.smithy.model.node.ExpectationNotMetException
         * @return Returns the extracted settings
         */
        fun from(model: Model, config: ObjectNode): KotlinSettings {
            config.warnIfAdditionalProperties(listOf(SERVICE, PACKAGE_SETTINGS, BUILD_SETTINGS, SDK_ID))

            val serviceId = config.getStringMember(SERVICE)
                .map(StringNode::expectShapeId)
                .orElseGet { inferService(model) }

            val packageNode = config.expectObjectMember(PACKAGE_SETTINGS)

            val packageName = packageNode.expectStringMember(PACKAGE_NAME).value
            if (!packageName.isValidPackageName()) {
                throw CodegenException("Invalid package name, is empty or has invalid characters: '$packageName'")
            }

            val version = packageNode.expectStringMember(PACKAGE_VERSION).value
            val desc = packageNode.getStringMemberOrDefault(PACKAGE_DESCRIPTION, "$packageName client")

            // Load the sdk id from configurations that define it, fall back to service name for those that don't.
            val sdkId = config.getStringMemberOrDefault(SDK_ID, serviceId.name)
            val build = config.getObjectMember(BUILD_SETTINGS)
            return KotlinSettings(serviceId, PackageSettings(packageName, version, desc), sdkId, BuildSettings.fromNode(build))
        }

        // infer the service to generate from a model
        internal fun inferService(model: Model): ShapeId {
            val services = model.shapes(ServiceShape::class.java)
                .map(Shape::getId)
                .sorted()
                .toList()

            when {
                services.isEmpty() -> {
                    throw CodegenException(
                        "Cannot infer a service to generate because the model does not " +
                            "contain any service shapes"
                    )
                }
                services.size > 1 -> {
                    throw CodegenException(
                        "Cannot infer service to generate because the model contains " +
                            "multiple service shapes: " + services
                    )
                }
                else -> {
                    val service = services[0]
                    LOGGER.info("Inferring service to generate as: $service")
                    return service
                }
            }
        }
    }

    /**
     * Resolves the highest priority protocol from a service shape that is
     * supported by the generator.
     *
     * @param serviceIndex Service index containing the support
     * @param service Service to get the protocols from if "protocols" is not set.
     * @param supportedProtocolTraits The set of protocol traits supported by the generator.
     * @return Returns the resolved protocol name.
     * @throws UnresolvableProtocolException if no protocol could be resolved.
     */
    fun resolveServiceProtocol(
        serviceIndex: ServiceIndex,
        service: ServiceShape,
        supportedProtocolTraits: Set<ShapeId>
    ): ShapeId {
        val resolvedProtocols: Set<ShapeId> = serviceIndex.getProtocols(service).keys
        val protocol = resolvedProtocols.firstOrNull(supportedProtocolTraits::contains)
        return protocol ?: throw UnresolvableProtocolException(
            "The ${service.id} service supports the following unsupported protocols $resolvedProtocols. " +
                "The following protocol generators were found on the class path: $supportedProtocolTraits"
        )
    }
}

data class BuildSettings(
    /**
     * Flag indicating to generate a full project that will exist independent of other projects
     */
    val generateFullProject: Boolean = false,

    /**
     * Flag indicating if (Gradle) build files should be spit out. This can be used to turn off generated gradle
     * files by default in-favor of e.g. spitting out your own custom Gradle file as part of an integration.
     */
    val generateDefaultBuildFiles: Boolean = true,

    /**
     * Kotlin opt-in annotations
     * See: https://kotlinlang.org/docs/reference/opt-in-requirements.html
     */
    val optInAnnotations: List<String>? = null
) {
    companion object {
        private const val ROOT_PROJECT = "rootProject"
        private const val GENERATE_DEFAULT_BUILD_FILES = "generateDefaultBuildFiles"
        private const val ANNOTATIONS = "optInAnnotations"

        fun fromNode(node: Optional<ObjectNode>): BuildSettings = if (node.isPresent) {
            val generateFullProject = node.get().getBooleanMemberOrDefault(ROOT_PROJECT, false)
            val generateBuildFiles = node.get().getBooleanMemberOrDefault(GENERATE_DEFAULT_BUILD_FILES, true)
            val annotations = node.get().getArrayMember(ANNOTATIONS).map {
                it.elements.mapNotNull {
                    it.asStringNode().map { it.value }.orNull()
                }
            }.orNull()

            BuildSettings(generateFullProject, generateBuildFiles, annotations)
        } else {
            Default
        }

        /**
         * Default build settings
         */
        val Default: BuildSettings = BuildSettings()
    }
}

class UnresolvableProtocolException(message: String) : CodegenException(message)

private fun <T> Optional<T>.orNull(): T? = if (isPresent) get() else null
