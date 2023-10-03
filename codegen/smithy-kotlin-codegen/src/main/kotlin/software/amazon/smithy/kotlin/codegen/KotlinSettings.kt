/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.lang.isValidPackageName
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex.CheckMode
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import java.util.Optional
import java.util.logging.Logger
import kotlin.IllegalArgumentException
import kotlin.streams.toList

// shapeId of service from which to generate an SDK
private const val SERVICE = "service"
private const val PACKAGE_SETTINGS = "package"
private const val PACKAGE_NAME = "name"
private const val PACKAGE_VERSION = "version"
private const val PACKAGE_DESCRIPTION = "description"
private const val BUILD_SETTINGS = "build"
private const val API_SETTINGS = "api"

// Optional specification of sdkId for models that provide them, otherwise Service's shape id name is used
private const val SDK_ID = "sdkId"

/**
 * Settings used by [KotlinCodegenPlugin]
 */
data class KotlinSettings(
    val service: ShapeId,
    val pkg: PackageSettings,
    val sdkId: String,
    val build: BuildSettings = BuildSettings.Default,
    val api: ApiSettings = ApiSettings.Default,
) {

    /**
     * Configuration elements specific to the service's package namespace, version, and description.
     */
    data class PackageSettings(val name: String, val version: String, val description: String? = null) {
        /**
         * Derive a subpackage namespace from the root package name
         */
        fun subpackage(subpackageName: String): String = "$name.$subpackageName"

        /**
         * The package namespace for generated serialization/deserialization support
         */
        val serde: String
            get() = subpackage("serde")
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
            config.warnIfAdditionalProperties(listOf(SERVICE, PACKAGE_SETTINGS, BUILD_SETTINGS, SDK_ID, API_SETTINGS))

            val serviceId = config.getStringMember(SERVICE)
                .map(StringNode::expectShapeId)
                .orElseGet { model.inferService().also { LOGGER.info("Inferring service to generate as $it") } }

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
            val api = config.getObjectMember(API_SETTINGS)
            return KotlinSettings(
                serviceId,
                PackageSettings(packageName, version, desc),
                sdkId,
                BuildSettings.fromNode(build),
                ApiSettings.fromNode(api),
            )
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
        supportedProtocolTraits: Set<ShapeId>,
    ): ShapeId {
        val resolvedProtocols: Set<ShapeId> = serviceIndex.getProtocols(service).keys
        val protocol = resolvedProtocols.firstOrNull(supportedProtocolTraits::contains)
        return protocol ?: throw UnresolvableProtocolException(
            "The ${service.id} service supports the following unsupported protocols $resolvedProtocols. " +
                "The following protocol generators were found on the class path: $supportedProtocolTraits",
        )
    }
}

fun Model.inferService(): ShapeId {
    val services = shapes(ServiceShape::class.java)
        .map(Shape::getId)
        .sorted()
        .toList()

    return when {
        services.isEmpty() -> {
            throw CodegenException(
                "Cannot infer a service to generate because the model does not contain any service shapes",
            )
        }
        services.size > 1 -> {
            throw CodegenException(
                "Cannot infer service to generate because the model contains multiple service shapes: $services",
            )
        }
        else -> services.single()
    }
}

/**
 * Contains Gradle build settings for a Kotlin project
 * @param generateFullProject Flag indicating to generate a full project that will exist independent of other projects
 * @param generateDefaultBuildFiles Flag indicating if (Gradle) build files should be spit out. This can be used to
 * turn off generated gradle files by default in-favor of e.g. spitting out your own custom Gradle file as part of an
 * integration.
 * @param optInAnnotations Kotlin opt-in annotations. See:
 * https://kotlinlang.org/docs/reference/opt-in-requirements.html
 * @param generateMultiplatformProject Flag indicating to generate a Kotlin multiplatform or JVM project
 */
data class BuildSettings(
    val generateFullProject: Boolean = false,
    val generateDefaultBuildFiles: Boolean = true,
    val optInAnnotations: List<String>? = null,
    val generateMultiplatformProject: Boolean = false,
) {
    companion object {
        const val ROOT_PROJECT = "rootProject"
        const val GENERATE_DEFAULT_BUILD_FILES = "generateDefaultBuildFiles"
        const val ANNOTATIONS = "optInAnnotations"
        const val GENERATE_MULTIPLATFORM_MODULE = "multiplatform"

        fun fromNode(node: Optional<ObjectNode>): BuildSettings = node.map {
            val generateFullProject = node.get().getBooleanMemberOrDefault(ROOT_PROJECT, false)
            val generateBuildFiles = node.get().getBooleanMemberOrDefault(GENERATE_DEFAULT_BUILD_FILES, true)
            val generateMultiplatformProject = node.get().getBooleanMemberOrDefault(GENERATE_MULTIPLATFORM_MODULE, false)
            val annotations = node.get().getArrayMember(ANNOTATIONS).map {
                it.elements.mapNotNull { node ->
                    node.asStringNode().map { stringNode ->
                        stringNode.value
                    }.orNull()
                }
            }.orNull()

            BuildSettings(generateFullProject, generateBuildFiles, annotations, generateMultiplatformProject)
        }.orElse(Default)

        /**
         * Default build settings
         */
        val Default: BuildSettings = BuildSettings()
    }
}

class UnresolvableProtocolException(message: String) : CodegenException(message)

private fun <T> Optional<T>.orNull(): T? = if (isPresent) get() else null

/**
 * The visibility of code-generated classes, objects, interfaces, etc.
 * Valid values are `public` and `internal`. `private` not supported because codegen would not compile with private classes.
 */
enum class Visibility(val value: String) {
    PUBLIC("public"),
    INTERNAL("internal"),
    ;

    override fun toString(): String = value

    companion object {
        public fun fromValue(value: String): Visibility = when (value.lowercase()) {
            "public" -> PUBLIC
            "internal" -> INTERNAL
            else -> throw IllegalArgumentException("$value is not a valid Visibility value, expected $PUBLIC or $INTERNAL")
        }
    }
}

private fun checkModefromValue(value: String): CheckMode {
    val camelCaseToMode = CheckMode.values().associateBy { it.toString().toCamelCase() }
    return requireNotNull(camelCaseToMode[value]) { "$value is not a valid CheckMode, expected one of ${camelCaseToMode.keys}" }
}

/**
 * Get the plugin setting for this check mode
 */
val CheckMode.kotlinPluginSetting: String
    get() = toString().toCamelCase()

enum class DefaultValueSerializationMode(val value: String) {
    /**
     * Always serialize default values even if they are set to the default
     */
    ALWAYS("always"),

    /**
     * Only serialize default values when they differ from the default given in the model.
     */
    WHEN_DIFFERENT("whenDifferent"),
    ;
    override fun toString(): String = value
    companion object {
        fun fromValue(value: String): DefaultValueSerializationMode =
            values().find {
                it.value == value
            } ?: throw IllegalArgumentException("$value is not a valid DefaultValueSerializationMode, expected one of ${values().map { it.value }}")
    }
}

/**
 * Contains API settings for a Kotlin project
 * @param visibility Enum representing the visibility of code-generated classes, objects, interfaces, etc.
 * @param nullabilityCheckMode Enum representing the nullability check mode to use
 * @param defaultValueSerializationMode Enum representing when default values should be serialized
 */
data class ApiSettings(
    val visibility: Visibility = Visibility.PUBLIC,
    val nullabilityCheckMode: CheckMode = CheckMode.CLIENT_CAREFUL,
    val defaultValueSerializationMode: DefaultValueSerializationMode = DefaultValueSerializationMode.WHEN_DIFFERENT,
) {
    companion object {
        const val VISIBILITY = "visibility"
        const val NULLABILITY_CHECK_MODE = "nullabilityCheckMode"
        const val DEFAULT_VALUE_SERIALIZATION_MODE = "defaultValueSerializationMode"

        fun fromNode(node: Optional<ObjectNode>): ApiSettings = node.map {
            val visibility = Visibility.fromValue(node.get().getStringMemberOrDefault(VISIBILITY, "public"))
            val checkMode = checkModefromValue(node.get().getStringMemberOrDefault(NULLABILITY_CHECK_MODE, "clientCareful"))
            val defaultValueSerializationMode = DefaultValueSerializationMode.fromValue(
                node.get()
                    .getStringMemberOrDefault(
                        DEFAULT_VALUE_SERIALIZATION_MODE,
                        DefaultValueSerializationMode.WHEN_DIFFERENT.value,
                    ),
            )
            ApiSettings(visibility, checkMode, defaultValueSerializationMode)
        }.orElse(Default)

        /**
         * Default build settings
         */
        val Default: ApiSettings = ApiSettings()
    }
}
