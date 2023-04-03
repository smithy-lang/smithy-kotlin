/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.kotlin.codegen.lang.isValidKotlinIdentifier
import software.amazon.smithy.kotlin.codegen.model.shape
import software.amazon.smithy.kotlin.codegen.utils.splitOnWordBoundaries
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.kotlin.codegen.utils.toPascalCase
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.EnumDefinition
import java.security.MessageDigest
import java.util.*
import java.util.logging.Logger

// (somewhat) centralized naming rules

/**
 * Get the default name for a shape (for code generation).  Delegates to
 * Smithy to rename shapes when configured to do so in the model.
 */
fun Shape.defaultName(serviceShape: ServiceShape): String = id.getName(serviceShape).toPascalCase()

/**
 * Get the default name for a member shape (for code generation), e.g., "bucketName"
 */
fun MemberShape.defaultName(): String = memberName.toCamelCase()

/**
 * Get the default name for a member shape (for code generation), e.g., "BucketName"
 */
fun MemberShape.capitalizedDefaultName(): String = defaultName().replaceFirstChar(Char::uppercaseChar)

/**
 * Get the default name for an operation shape, e.g., "listOperations"
 */
fun OperationShape.defaultName(): String = id.name.toCamelCase()

/**
 * Get the capitalized default name for an operation shape, e.g., "ListOperations"
 */
fun OperationShape.capitalizedDefaultName(): String = defaultName().replaceFirstChar(Char::uppercaseChar)

private fun String.sanitizeClientName(): String =
    replace(Regex("(API|Client|Service)\$", setOf(RegexOption.IGNORE_CASE)), "")

/**
 * Get the generated SDK service client name to use.
 * See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
 *
 * @param raw The raw string that represents the service name (usually settings.sdkId)
 */
fun clientName(raw: String): String = raw.sanitizeClientName().toPascalCase()

/**
 * Get the (un-validated) name of an enum variant.
 *
 * This value can come from an enum definition trait, or it could be a member name from an explicit enum shape.
 */
fun String.enumVariantName(): String {
    val identifier = splitOnWordBoundaries()
        .fold(StringBuilder()) { acc, x ->
            val curr = x.lowercase().replaceFirstChar { c -> c.uppercaseChar() }
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
fun MemberShape.unionVariantName(): String = this.memberName.toPascalCase()

/**
 * Get the name of the middleware registration function for an operation
 * e.g. `register{OperationName}Middleware
 */
fun OperationShape.registerMiddlewareName(): String = "register${this.capitalizedDefaultName()}Middleware"

/**
 * Generate a mangled name based on the [shape] and the members contained in [members]
 * If the set of [members] contains all members of [shape] then an empty suffix is returned.
 */
internal fun Shape.mangledSuffix(members: Collection<MemberShape> = members()): String {
    check(members().containsAll(members)) { "One or more members given $members is not a member of $this" }
    if (members().size == members.size) return ""

    val md = MessageDigest.getInstance("SHA-256")
    md.update(id.toString().encodeToByteArray())

    members.forEach { md.update(it.id.toString().encodeToByteArray()) }

    val b64Encoder = Base64.getUrlEncoder().withoutPadding()
    val encoded = b64Encoder.encodeToString(md.digest())

    return encoded.filter { it.isLetterOrDigit() }.substring(0, 8)
}
