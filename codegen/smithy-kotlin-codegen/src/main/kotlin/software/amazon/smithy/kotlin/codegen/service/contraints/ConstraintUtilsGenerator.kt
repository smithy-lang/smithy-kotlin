package software.amazon.smithy.kotlin.codegen.service.contraints

import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock

internal class ConstraintUtilsGenerator(
    val ctx: GenerationContext,
    val delegator: KotlinDelegator,
) {
    val pkgName = ctx.settings.pkg.name

    fun render() {
        delegator.useFileWriter("utils.kt", "$pkgName.constraints") { writer ->
            renderLengthTraitUtils(writer)

            writer.write("")
            renderUniqueItemsTraitUtils(writer)
        }
    }

    private fun renderLengthTraitUtils(writer: KotlinWriter) {
        writer.withBlock("internal fun sizeOf(value: Any?): Long = when (value) {", "}") {
            write("is Collection<*> -> value.size.toLong()")
            write("is Array<*> -> value.size.toLong()")
            write("is Map<*, *> -> value.size.toLong()")
            write("is String -> value.codePointCount(0, value.length).toLong()")
            write("is ByteArray -> value.size.toLong()")
            withBlock("else -> {", "}") {
                write("val typeName = value?.javaClass?.simpleName ?: #S", "null")
                write("throw IllegalArgumentException( #S )", "sizeOf does not support \${typeName} type")
            }
        }
    }

    private fun renderUniqueItemsTraitUtils(writer: KotlinWriter) {
        writer.withBlock("internal fun hasAllUniqueElements(elements: List<Any?>): Boolean {", "}") {
            withBlock("class Wrapped(private val v: Any?) {", "}") {
                withBlock("override fun equals(other: Any?): Boolean {", "}") {
                    write("if (other !is Wrapped) return false")
                    write("if (v?.javaClass != other.v?.javaClass) return false")
                    withBlock("return when (v) {", "}") {
                        write("null -> true")
                        write("is String,")
                        write("is Boolean,")
                        write("is java.time.Instant,")
                        write("is Number -> v == other.v")
                        write("is ByteArray -> v.contentEquals(other.v as ByteArray)")
                        withBlock("is List<*> -> {", "}") {
                            write("val o = other.v as List<*>")
                            write("v.size == o.size &&  v.indices.all { i -> Wrapped(v[i]) == Wrapped(o[i]) }")
                        }
                        withBlock("is Map<*, *>  -> {", "}") {
                            write("val o = other.v as Map<*, *>")
                            write("v.size == o.size && v.all { (k, value) -> o.containsKey(k) && Wrapped(value) == Wrapped(o[k]) }")
                        }
                        write("else -> v == other.v")
                    }
                }
                withBlock("override fun hashCode(): Int = when (v) {", "}") {
                    write("null -> 0")
                    write("is ByteArray -> v.contentHashCode()")
                    write("is List<*> -> v.fold(1) { acc, e -> 31 * acc + Wrapped(e).hashCode() }")
                    write("is Map<*, *> -> v.entries.fold(1) { acc, (k, e) -> 31 * acc + Wrapped(k).hashCode() xor Wrapped(e).hashCode() }")
                    write("else -> v.hashCode()")
                }
            }
            write("")
            write("val seen = HashSet<Wrapped>(elements.size)")
            write("for (e in elements) if (!seen.add(Wrapped(e))) return false")
            write("return true")
        }
    }
}
