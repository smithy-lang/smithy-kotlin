package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.TimestampFormatTrait

fun renderCastingPrimitiveFromShapeType(
    variable: String,
    type: ShapeType,
    writer: KotlinWriter,
    timestampFormatTrait: TimestampFormatTrait? = null,
    errorMessage: String? = null,
) {
    when (type) {
        ShapeType.BLOB -> writer.write("$variable?.toByteArray()")
        ShapeType.STRING -> writer.write("$variable?.toString()")
        ShapeType.BYTE -> writer.write("$variable?.toByte()")
        ShapeType.INTEGER -> writer.write("$variable?.toInt()")
        ShapeType.SHORT -> writer.write("$variable?.toShort()")
        ShapeType.LONG -> writer.write("$variable?.toLong()")
        ShapeType.FLOAT -> writer.write("$variable?.toFloat()")
        ShapeType.DOUBLE -> writer.write("$variable?.toDouble()")
        ShapeType.BIG_DECIMAL -> writer.write("$variable?.toBigDecimal()")
        ShapeType.BIG_INTEGER -> writer.write("$variable?.toBigInteger()")
        ShapeType.BOOLEAN -> writer.write("$variable?.toBoolean()")
        ShapeType.TIMESTAMP ->
            when (timestampFormatTrait?.format) {
                TimestampFormatTrait.Format.EPOCH_SECONDS ->
                    writer.write("$variable?.let{ #T.fromEpochSeconds(it) }", RuntimeTypes.Core.Instant)
                TimestampFormatTrait.Format.DATE_TIME ->
                    writer.write("$variable?.let{ #T.fromIso8601(it) }", RuntimeTypes.Core.Instant)
                TimestampFormatTrait.Format.HTTP_DATE ->
                    writer.write("$variable?.let{ #T.fromRfc5322(it) }", RuntimeTypes.Core.Instant)
                else -> writer.write("$variable?.let{ #T.fromIso8601(it) }", RuntimeTypes.Core.Instant)
            }
        else -> throw IllegalStateException(errorMessage ?: "Unable to render casting primitive for $type")
    }
}
