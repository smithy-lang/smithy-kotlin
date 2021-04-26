/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.test

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.integration.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * This file houses test classes and functions relating to the code generator (protocols, serializers, etc)
 *
 * Items contained here should be relatively high-level, utilizing all members of codegen classes, Smithy, and
 * anything else necessary for test functionality.
 */

/**
 * Container for type instances necessary for tests
 */
internal data class TestContext(
    val generationCtx: ProtocolGenerator.GenerationContext,
    val manifest: MockManifest,
    val generator: ProtocolGenerator
)

// Execute the codegen and return the generated output
internal fun testRender(
    members: List<MemberShape>,
    renderFn: (List<MemberShape>, KotlinWriter) -> Unit
): String {
    val writer = KotlinWriter(TestModelDefault.NAMESPACE)
    renderFn(members, writer)
    return writer.toString()
}

// Drive codegen for serialization of a given shape
internal fun codegenSerializerForShape(model: Model, shapeId: String, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): String {
    val ctx = model.newTestContext()

    val op = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))
    return testRender(ctx.requestMembers(op, location)) { members, writer ->
        SerializeStructGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()
    }
}

// Drive codegen for deserialization of a given shape
internal fun codegenDeserializerForShape(model: Model, shapeId: String, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): String {
    val ctx = model.newTestContext()
    val op = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))

    return testRender(ctx.responseMembers(op, location)) { members, writer ->
        DeserializeStructGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()
    }
}

// Drive codegen for serializer of a union of a given shape
internal fun codegenUnionSerializerForShape(model: Model, shapeId: String): String {
    val ctx = model.newTestContext()

    val testMembers = when (val shape = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))) {
        is OperationShape -> {
            val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
            val requestBindings = bindingIndex.getRequestBindings(shape)
            val unionShape = ctx.generationCtx.model.expectShape(requestBindings.values.first().member.target)
            unionShape.members().toList().sortedBy { it.memberName }
        }
        is StructureShape -> {
            shape.members().toList().sortedBy { it.memberName }
        }
        else -> throw RuntimeException("unknown conversion for $shapeId")
    }

    return testRender(testMembers) { members, writer ->
        SerializeUnionGenerator(
            ctx.generationCtx,
            members,
            writer,
            TimestampFormatTrait.Format.EPOCH_SECONDS
        ).render()
    }
}

// Retrieves Response Document members for HttpTrait-enabled protocols
internal fun TestContext.responseMembers(shape: Shape, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getResponseBindings(shape)

    return responseBindings.values
        .filter { it.location == location }
        .sortedBy { it.memberName }
        .map { it.member }
}

// Retrieves Request Document members for HttpTrait-enabled protocols
internal fun TestContext.requestMembers(shape: Shape, location: HttpBinding.Location = HttpBinding.Location.DOCUMENT): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getRequestBindings(shape)

    return responseBindings.values
        .filter { it.location == location }
        .sortedBy { it.memberName }
        .map { it.member }
}

internal fun TestContext.toGenerationContext(): GenerationContext =
    GenerationContext(generationCtx.model, generationCtx.symbolProvider, generationCtx.settings, generator)

internal fun <T : Shape> TestContext.toRenderingContext(writer: KotlinWriter, forShape: T? = null): RenderingContext<T> =
    toGenerationContext().toRenderingContext(writer, forShape)

// A HttpProtocolClientGenerator for testing
internal class TestProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    features: List<ProtocolMiddleware>,
    httpBindingResolver: HttpBindingResolver
) : HttpProtocolClientGenerator(ctx, features, httpBindingResolver) {
    // This type assumes a JSON based protocol, but can be changed to pass
    // in format if necessary.
    override val serdeProviderSymbol: Symbol = buildSymbol {
        name = "JsonSerdeProvider"
        namespace(KotlinDependency.CLIENT_RT_SERDE_JSON)
    }
}

// A HttpBindingProtocolGenerator for testing
internal class MockHttpProtocolGenerator : HttpBindingProtocolGenerator() {
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS
    override fun getProtocolHttpBindingResolver(ctx: ProtocolGenerator.GenerationContext): HttpBindingResolver =
        HttpTraitResolver(ctx, "application/json")

    override val protocol: ShapeId = RestJson1Trait.ID

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {}

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        TestProtocolClientGenerator(ctx, getHttpMiddleware(ctx), getProtocolHttpBindingResolver(ctx))

    override fun generateSdkFieldDescriptor(
        ctx: ProtocolGenerator.GenerationContext,
        memberShape: MemberShape,
        writer: KotlinWriter,
        memberTargetShape: Shape?,
        namePostfix: String
    ) { }

    override fun generateSdkObjectDescriptorTraits(
        ctx: ProtocolGenerator.GenerationContext,
        objectShape: Shape,
        writer: KotlinWriter
    ) { }
}

// Create a test harness with all necessary codegen types
fun codegenTestHarnessForModelSnippet(
    generator: ProtocolGenerator,
    namespace: String = TestModelDefault.NAMESPACE,
    serviceName: String = TestModelDefault.SERVICE_NAME,
    operations: List<String>,
    snippet: () -> String
): CodegenTestHarness {
    val protocol = generator.protocol.name
    val model = snippet().generateTestModel(protocol, namespace, serviceName, operations)
    val ctx = model.generateTestContext(namespace, serviceName)
    val manifest = ctx.delegator.fileManifest as MockManifest

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
    val protocol: String
)

// Drive de/serializer codegen and return results in map indexed by filename.
fun CodegenTestHarness.generateDeSerializers(): Map<String, String> {
    generator.generateSerializers(generationCtx)
    generator.generateDeserializers(generationCtx)
    generationCtx.delegator.flushWriters()
    return manifest.files.associate { path -> path.fileName.toString() to manifest.expectFileString(path) }
}

// Create and use a writer to drive codegen from a function taking a writer.
// Strip off comment and package preamble.
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
