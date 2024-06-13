/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.utils.doubleQuote
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.rulesengine.language.evaluation.value.BooleanValue
import software.amazon.smithy.rulesengine.language.evaluation.value.StringValue
import software.amazon.smithy.rulesengine.language.evaluation.value.Value
import software.amazon.smithy.rulesengine.language.syntax.Identifier
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType

/**
 * Derive the kotlin variable name for an identifier.
 */
fun Identifier.defaultName(): String = toString().toCamelCase()

/**
 * Derive the kotlin variable name for an endpoint parameter.
 */
fun Parameter.defaultName(): String = name.defaultName()

/**
 * Get the symbol for an endpoint parameter type. All endpoint parameter members are nullable.
 */
fun ParameterType.toSymbol(): Symbol =
    when (this) {
        ParameterType.STRING -> KotlinTypes.String
        ParameterType.BOOLEAN -> KotlinTypes.Boolean
        ParameterType.STRING_ARRAY -> KotlinTypes.Collections.MutableList
    }.asNullable()

/**
 * Get the writable literal for a rules engine value.
 */
fun Value.toLiteral(): String =
    when (this) {
        is StringValue -> value.doubleQuote()
        is BooleanValue -> value.toString()
        else -> throw IllegalArgumentException("unrecognized parameter value type $type")
    }
