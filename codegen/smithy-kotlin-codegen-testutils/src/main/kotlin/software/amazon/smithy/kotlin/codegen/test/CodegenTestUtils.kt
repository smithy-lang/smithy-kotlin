/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.test

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.utils.StringUtils

// This file houses test classes and functions relating to the code generator (protocols, serializers, etc)
// Items contained here should be relatively high-level, utilizing all members of codegen classes, Smithy, and
// anything else necessary for test functionality.

/**
 * Container for type instances necessary for tests
 */
data class TestContext(
    val generationCtx: ProtocolGenerator.GenerationContext,
    val manifest: MockManifest,
    val generator: ProtocolGenerator,
)

/** Execute the codegen and return the generated output */
fun testRender(
    members: List<MemberShape>,
    renderFn: (List<MemberShape>, KotlinWriter) -> Unit,
): String {
    val writer = KotlinWriter(TestModelDefault.NAMESPACE)
    renderFn(members, writer)
    return writer.toString()
}

/** Drive codegen for serialization of a given shape */
fun codegenSerializerForShape(model: Model, shapeId: String, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): String {
    val ctx = model.newTestContext()

    val op = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))
    return testRender(ctx.requestMembers(op, location)) { members, writer ->
        SerializeStructGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS,
        ).render()
    }
}

/** Drive codegen for deserialization of a given shape */
fun codegenDeserializerForShape(model: Model, shapeId: String, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): String {
    val ctx = model.newTestContext()
    val op = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))

    return testRender(ctx.responseMembers(op, location)) { members, writer ->
        DeserializeStructGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS,
        ).render()
    }
}

/** Drive codegen for serializer of a union of a given shape */
fun codegenUnionSerializerForShape(model: Model, shapeId: String): String {
    val ctx = model.newTestContext()

    val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
    val operationShape = ctx.generationCtx.model.expectShape<OperationShape>(shapeId)
    val requestBindings = bindingIndex.getRequestBindings(operationShape)
    val unionShape = ctx.generationCtx.model.expectShape<UnionShape>(requestBindings.values.first().member.target)
    val testMembers = unionShape.members().toList().sortedBy { it.memberName }

    return testRender(testMembers) { members, writer ->
        SerializeUnionGenerator(
            ctx.generationCtx,
            unionShape,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS,
        ).render()
    }
}

/** Retrieves response document members for HttpTrait-enabled protocols */
fun TestContext.responseMembers(shape: Shape, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getResponseBindings(shape)

    return responseBindings.values
        .filter { it.location == location }
        .sortedBy { it.memberName }
        .map { it.member }
}

/** Retrieves Request Document members for HttpTrait-enabled protocols */
fun TestContext.requestMembers(shape: Shape, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getRequestBindings(shape)

    return responseBindings.values
        .filter { it.location == location }
        .sortedBy { it.memberName }
        .map { it.member }
}

fun TestContext.toGenerationContext(): GenerationContext =
    GenerationContext(generationCtx.model, generationCtx.symbolProvider, generationCtx.settings, generator)

fun <T : Shape> TestContext.toRenderingContext(writer: KotlinWriter, forShape: T? = null): RenderingContext<T> =
    toGenerationContext().toRenderingContext(writer, forShape)

/** An HttpProtocolClientGenerator for testing */
class TestProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    features: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver,
) : HttpProtocolClientGenerator(ctx, features, httpBindingResolver)

private val allProtocols = setOf(
    AwsJson1_0Trait.ID,
    AwsJson1_1Trait.ID,
    AwsQueryTrait.ID,
    Ec2QueryTrait.ID,
    RestJson1Trait.ID,
    RestXmlTrait.ID,
)

/** An HttpBindingProtocolGenerator for testing (nothing is rendered for serializing/deserializing payload bodies) */
class MockHttpProtocolGenerator(model: Model) : HttpBindingProtocolGenerator() {
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS
    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, ProtocolContentTypes.consistent("application/json"))

    override val protocol: ShapeId = model
        .serviceShapes
        .single()
        .allTraits
        .values
        .map(Trait::toShapeId)
        .singleOrNull(allProtocols::contains)
        ?: RestJson1Trait.ID

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {}

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        TestProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx.model, ctx.service))

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        object : StructuredDataParserGenerator {
            override fun operationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol = buildSymbol {
                name = op.bodyDeserializerName()
            }

            override fun errorDeserializer(
                ctx: ProtocolGenerator.GenerationContext,
                errorShape: StructureShape,
                members: List<MemberShape>,
            ): Symbol = buildSymbol {
                val errSymbol = ctx.symbolProvider.toSymbol(errorShape)
                name = errSymbol.errorDeserializerName()
            }

            override fun payloadDeserializer(
                ctx: ProtocolGenerator.GenerationContext,
                shape: Shape,
                members: Collection<MemberShape>?,
            ): Symbol = buildSymbol {
                val symbol = ctx.symbolProvider.toSymbol(shape)
                name = "deserialize" + StringUtils.capitalize(symbol.name) + "Payload"
            }
        }

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        object : StructuredDataSerializerGenerator {
            override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol = buildSymbol {
                name = op.bodySerializerName()
            }

            override fun payloadSerializer(
                ctx: ProtocolGenerator.GenerationContext,
                shape: Shape,
                members: Collection<MemberShape>?,
            ): Symbol = buildSymbol {
                val symbol = ctx.symbolProvider.toSymbol(shape)
                name = "serialize" + StringUtils.capitalize(symbol.name) + "Payload"
            }
        }

    override fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol = buildSymbol {
        name = op.errorHandlerName()
    }
}

/** Create a test harness with all necessary codegen types */
fun codegenTestHarnessForModelSnippet(
    generator: ProtocolGenerator,
    namespace: String = TestModelDefault.NAMESPACE,
    serviceName: String = TestModelDefault.SERVICE_NAME,
    operations: List<String>,
    snippet: () -> String,
): CodegenTestHarness {
    val protocol = generator.protocol.name
    val model = snippet().generateTestModel(protocol, namespace, serviceName, operations)
    val (ctx, manifest, _) = model.newTestContext(serviceName = serviceName, packageName = namespace, generator = generator)

    return CodegenTestHarness(ctx, manifest, generator, namespace, serviceName, protocol)
}

/**
 * Contains references to all types necessary to drive and validate codegen.
 */
data class CodegenTestHarness(
    val generationCtx: ProtocolGenerator.GenerationContext,
    val manifest: MockManifest,
    val generator: ProtocolGenerator,
    val namespace: String,
    val serviceName: String,
    val protocol: String,
)

/**
 * Create and use a writer to drive codegen from a function taking a writer.
 * Strip off comment and package preamble.
 */
fun generateCode(generator: (KotlinWriter) -> Unit): String {
    val packageDeclaration = "some-unique-thing-that-will-never-be-codegened"
    val writer = KotlinWriter(packageDeclaration)
    generator.invoke(writer)
    val rawCodegen = writer.toString()
    return rawCodegen.substring(rawCodegen.indexOf(packageDeclaration) + packageDeclaration.length).trim()
}

fun KotlinCodegenPlugin.Companion.createSymbolProvider(model: Model, rootNamespace: String = TestModelDefault.NAMESPACE, sdkId: String = TestModelDefault.SDK_ID, serviceName: String = TestModelDefault.SERVICE_NAME): SymbolProvider {
    val settings = model.defaultSettings(serviceName = serviceName, packageName = rootNamespace, sdkId = sdkId)
    return createSymbolProvider(model, settings)
}

/**
 * create a new [KotlinWriter] using the test context package name
 */
fun TestContext.newWriter(): KotlinWriter = KotlinWriter(generationCtx.settings.pkg.name)
