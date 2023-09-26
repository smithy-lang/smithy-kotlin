/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.model.isEnum
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeType

object ClientErrorCorrection {
    /**
     * Determine the default value for a required member based on
     * [client error correction](https://smithy.io/2.0/spec/aggregate-types.html?highlight=error%20correction#client-error-correction)
     *
     * @param ctx the generation context
     * @param member the target member shape to get the default value for
     * @param writer the writer the default value will be written to, this is used for certain shapes to format the
     * default value which will mutate the writer (e.g. add imports).
     * @return default value expression as a string
     */
    fun defaultValue(
        ctx: CodegenContext,
        member: MemberShape,
        writer: KotlinWriter,
    ): String {
        val target = ctx.model.expectShape(member.target)
        val targetSymbol = ctx.symbolProvider.toSymbol(target)

        // In IDL v1 all enums were `ShapeType.STRING` and you had to explicitly check for the @enum trait, this handles
        // the differences in IDL versions
        if (target.isEnum) {
            return writer.format("#T.SdkUnknown(#S)", targetSymbol, "no value provided")
        }

        return when (target.type) {
            ShapeType.BLOB -> "ByteArray(0)"
            ShapeType.BOOLEAN -> "false"
            ShapeType.STRING -> "\"\""
            ShapeType.BYTE -> "0.toByte()"
            ShapeType.SHORT -> "0.toShort()"
            ShapeType.INTEGER -> "0"
            ShapeType.LONG -> "0L"
            ShapeType.FLOAT -> "0f"
            ShapeType.DOUBLE -> "0.0"
            ShapeType.BIG_INTEGER -> writer.format("#T(\"0\")", RuntimeTypes.Core.Content.BigInteger)
            ShapeType.BIG_DECIMAL -> writer.format("#T(\"0\")", RuntimeTypes.Core.Content.BigDecimal)
            ShapeType.DOCUMENT -> "null"
            ShapeType.UNION -> writer.format("#T.SdkUnknown", targetSymbol)
            ShapeType.LIST,
            ShapeType.SET,
            -> "emptyList()"
            ShapeType.MAP -> "emptyMap()"
            ShapeType.STRUCTURE -> writer.format("#T.Builder().correctErrors().build()", targetSymbol)
            ShapeType.TIMESTAMP -> writer.format("#T.fromEpochSeconds(0)", RuntimeTypes.Core.Instant)
            else -> throw CodegenException("unexpected member type $member")
        }
    }
}
