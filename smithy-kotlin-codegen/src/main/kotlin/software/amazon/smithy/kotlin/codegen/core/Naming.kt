/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.lang.isValidKotlinIdentifier
import software.amazon.smithy.kotlin.codegen.utils.splitOnWordBoundaries
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.kotlin.codegen.utils.toPascalCase
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.EnumDefinition
import java.util.logging.Logger

// (somewhat) centralized naming rules

/**
 * Get the default name for a shape (for code generation).  Delegates to
 * Smithy to rename shapes when configured to do so in the model.
 */
fun Shape.defaultName(serviceShape: ServiceShape): String = id.getName(serviceShape).toPascalCase()

/**
 * Get the default name for a member shape (for code generation)
 */
fun MemberShape.defaultName(): String = memberName.toCamelCase()

/**
 * Get the default name for an operation shape
 */
fun OperationShape.defaultName(): String = id.name.toCamelCase()

/**
 * Get the generated SDK service client name to use. The target should be a string that represents the `sdkId`
 * of the service.
 *
 * See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
 */
fun String.clientName(): String = toPascalCase()

/**
 * Get the (un-validated) name of an enum variant from the trait definition
 */
fun EnumDefinition.variantName(): String {
    val identifier = name.orElseGet {
        // we don't want to be doing this...name your enums people
        Logger.getLogger("NamingUtils").also {
            it.warning("Using EnumDefinition.value to derive generated identifier name: $value")
        }
        value
    }
        .splitOnWordBoundaries()
        .fold(StringBuilder()) { acc, x ->
            val curr = x.toLowerCase().capitalize()
            if (acc.isNotEmpty() && acc.last().isDigit() && x.first().isDigit()) {
                // split on word boundaries created distinct words for adjacent digits e.g. "11.4" -> ["11" "4"]
                // separate these out with _ as they are likely versions strings of some sort where a separation
                // gives meaning
                acc.append("_")
            }
            acc.append(curr)
            acc
        }.toString()
        .replace(Regex("([0-9]+_[0-9]+)([a-zA-Z])"), "$1_$2") // increase visual separation: "NodeJs_1_2Edge" -> "NodeJs_1_2_Edge"

    return when (isValidKotlinIdentifier(identifier)) {
        true -> identifier
        // attempt to prefix it (e.g. `0` -> `_0`)
        false -> "_$identifier"
    }
}

/**
 * Generate the union variant name from a union member shape
 * e.g. `VariantName`
 */
fun MemberShape.unionVariantName(symbolProvider: SymbolProvider): String = symbolProvider
    .toMemberName(this)
    .capitalize()
