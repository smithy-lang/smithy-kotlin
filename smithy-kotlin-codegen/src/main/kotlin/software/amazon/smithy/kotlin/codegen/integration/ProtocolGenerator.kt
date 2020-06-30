/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.utils.CaseUtils

/**
 * Smithy protocol code generator(s)
 */
interface ProtocolGenerator {

    /**
     * Sanitizes the name of the protocol so it can be used as a symbol in Kotlin.
     *
     * For example, the default implementation converts '.' to '_' and converts '-'
     * to become camelCase separated words. `aws.rest-json-1.1` becomes `Aws_RestJson1_1`
     *
     * @param name Name of the protocol to sanitize
     * @return sanitized protocol name
     */
    fun getSanitizedName(name: String): String {
        val result = name.replace("(\\s|\\.|-)".toRegex(), "_")
        return CaseUtils.toCamelCase(name, true, '_')
    }

    /**
     * Get the supported protocol [ShapeId]
     * e.g. `software.amazon.smithy.aws.traits.protocols.RestJson1Trait.ID`
     */
    val protocol: ShapeId

    /**
     * Context object used for service serialization and deserialization
     */
    data class GenerationContext(
        val settings: KotlinSettings,
        val model: Model,
        val service: ServiceShape,
        val symbolProvider: SymbolProvider,
        val writer: KotlinWriter,
        val integrations: List<KotlinIntegration>,
        val protocolName: String,
        val delegator: KotlinDelegator
    )
}
