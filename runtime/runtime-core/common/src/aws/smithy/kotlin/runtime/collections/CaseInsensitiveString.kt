package aws.smithy.kotlin.runtime.collections

internal class CaseInsensitiveString(val original: String) {
    val normalized = original.lowercase()
    override fun hashCode() = normalized.hashCode()
    override fun equals(other: Any?) = other is CaseInsensitiveString && normalized == other.normalized
    override fun toString() = original
}

internal fun String.toInsensitive(): CaseInsensitiveString =
    CaseInsensitiveString(this)
