package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator

enum class ServiceFramework(val value: String) {
    KTOR("ktor"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ServiceFramework = when (value.lowercase()) {
            "ktor" -> KTOR
            else -> throw IllegalArgumentException("$value is not a valid ServerFramework value, expected $KTOR")
        }
    }

    internal fun getServiceFrameworkGenerator(
        ctx: GenerationContext,
        delegator: KotlinDelegator,
        fileManifest: FileManifest,
    ): AbstractStubGenerator {
        when (this) {
            KTOR -> return KtorStubGenerator(ctx, delegator, fileManifest)
        }
    }
}
