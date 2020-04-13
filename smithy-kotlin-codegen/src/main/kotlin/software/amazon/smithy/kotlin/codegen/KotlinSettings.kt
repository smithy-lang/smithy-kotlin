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

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import java.util.logging.Logger
import kotlin.streams.toList

private const val SERVICE = "service"
private const val MODULE_NAME = "module"
private const val MODULE_DESCRIPTION = "moduleDescription"
private const val MODULE_VERSION = "moduleVersion"

/**
 * Settings used by [KotlinCodegenPlugin]
 */
class KotlinSettings(
    val service: ShapeId,
    val moduleName: String,
    val moduleVersion: String,
    val moduleDescription: String = ""
) {

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
            config.warnIfAdditionalProperties(arrayListOf(SERVICE, MODULE_NAME, MODULE_DESCRIPTION, MODULE_VERSION))

            val service = config.getStringMember(SERVICE)
                .map(StringNode::expectShapeId)
                .orElseGet { inferService(model) }

            val moduleName = config.expectStringMember(MODULE_NAME).value
            val version = config.expectStringMember(MODULE_VERSION).value
            val desc = config.getStringMemberOrDefault(MODULE_DESCRIPTION, "$moduleName client")
            return KotlinSettings(service, moduleName, version, desc)
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
                        "Cannot infer a service to generate because the model does not "
                                + "contain any service shapes"
                    )
                }
                services.size > 1 -> {
                    throw CodegenException(
                        "Cannot infer service to generate because the model contains "
                                + "multiple service shapes: " + services
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

