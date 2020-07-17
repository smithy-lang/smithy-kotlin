/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.kotlin.codegen

import java.util.Optional
import java.util.logging.Logger
import kotlin.streams.toList
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId

private const val SERVICE = "service"
private const val MODULE_NAME = "module"
private const val MODULE_DESCRIPTION = "moduleDescription"
private const val MODULE_VERSION = "moduleVersion"
private const val BUILD_SETTINGS = "build"

/**
 * Settings used by [KotlinCodegenPlugin]
 */
class KotlinSettings(
    val service: ShapeId,
    val moduleName: String,
    val moduleVersion: String,
    val moduleDescription: String = "",
    val build: BuildSettings
) {

    /**
     * Get the corresponding [ServiceShape] from a model.
     * @return Returns the found `Service`
     * @throws CodegenException if the service is invalid or not found
     */
    fun getService(model: Model): ServiceShape {
        return model
            .getShape(service)
            .orElseThrow { CodegenException("Service shape not found: $service") }
            .asServiceShape()
            .orElseThrow { CodegenException("Shape is not a service: $service") }
    }

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
            config.warnIfAdditionalProperties(arrayListOf(SERVICE, MODULE_NAME, MODULE_DESCRIPTION, MODULE_VERSION, BUILD_SETTINGS))

            val service = config.getStringMember(SERVICE)
                .map(StringNode::expectShapeId)
                .orElseGet { inferService(model) }

            val moduleName = config.expectStringMember(MODULE_NAME).value
            val version = config.expectStringMember(MODULE_VERSION).value
            val desc = config.getStringMemberOrDefault(MODULE_DESCRIPTION, "$moduleName client")
            val build = config.getObjectMember(BUILD_SETTINGS)
            return KotlinSettings(service, moduleName, version, desc, BuildSettings.fromNode(build))
        }

        // infer the service to generate from a model
        private fun inferService(model: Model): ShapeId {
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
}

data class BuildSettings(val rootProject: Boolean = false) {
    companion object {
        private const val ROOT_PROJECT = "rootProject"
        fun fromNode(node: Optional<ObjectNode>): BuildSettings {
            return if (node.isPresent) {
                BuildSettings(node.get().getMember(BuildSettings.ROOT_PROJECT).get().asBooleanNode().get().value)
            } else {
                Default()
            }
        }
        fun Default(): BuildSettings = BuildSettings(false)
    }
}
