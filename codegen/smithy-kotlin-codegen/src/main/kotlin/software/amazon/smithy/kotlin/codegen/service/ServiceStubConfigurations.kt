package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.HttpPayloadTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait

enum class ContentType(val value: String) {
    CBOR("CBOR"),
    JSON("JSON"),
    PLAIN_TEXT("PLAIN_TEXT"),
    BINARY("BINARY"),
    MEDIA_TYPE("MEDIA_TYPE"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ContentType = ContentType
            .entries
            .firstOrNull { it.name.equals(value.uppercase(), ignoreCase = true) }
            ?: throw IllegalArgumentException("$value is not a validContentType value, expected one of ${ContentType.entries}")

        fun fromServiceShape(ctx: GenerationContext, shape: ServiceShape, operation: OperationShape): ContentType {
            return when {
                shape.hasTrait(Rpcv2CborTrait.ID) -> CBOR
                shape.hasTrait(RestJson1Trait.ID) -> {
                    println(shape.allMembers)
                    val inputShape = ctx.model.expectShape(operation.input.get())
                    for (memberShape in inputShape.allMembers.values) {
                        println("------------------------------")
                        println(memberShape)
                        if (!memberShape.hasTrait(HttpPayloadTrait.ID)) continue
                        val memberType = ctx.model.expectShape(memberShape.target).type
                        println(memberType)
                        when (memberType) {
                            ShapeType.STRING -> return PLAIN_TEXT
                            ShapeType.DOCUMENT,
                            ShapeType.STRUCTURE,
                            ShapeType.UNION,
                            -> return JSON
                            else -> {
                                if (memberShape.hasTrait(MediaTypeTrait.ID)) return MEDIA_TYPE
                            }
                        }
                    }
                    return JSON
                }

                else -> throw IllegalArgumentException("service shape does not a valid protocol")
            }
        }
    }
}

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
