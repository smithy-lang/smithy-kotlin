package software.amazon.smithy.kotlin.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.integration.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.integration.SerializeStructGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

class ApiEvolutionTest {

    @Test
    fun `operation with no input to with input does not break`() {
        // Create no input model
        val model1 = """
            namespace com.test

            use aws.protocols#awsJson1_1

            @awsJson1_1
            service Example {
                version: "1.0.0",
                operations: [
                    GetFooNoInput,
                ]
            }

            structure GetFooRequest {}

            @http(method: "POST", uri: "/foo-no-input")
            operation GetFooNoInput {
                input: GetFooRequest
            }
        """.asSmithy()

        // Generate output
        val sdkSources = generateSdk(model1) { ApplicationProtocol.createDefaultHttpApplicationProtocol() }

        // Run test against

        val result = KotlinCompilation().apply {
            sources = sdkSources
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)

        // Create model w/ input
        val model2 = """
            namespace com.test

            use aws.protocols#awsJson1_1

            @awsJson1_1
            service Example {
                version: "1.0.0",
                operations: [
                    GetFooWithInput,
                ]
            }

            structure GetFooRequest {
                body: String
            }
            
            structure GetFooResponse {}

            @http(method: "POST", uri: "/foo-no-input")
            operation GetFooWithInput {
                input: GetFooRequest,
                output: GetFooResponse
            }
        """.asSmithy()
        // Generate output

        // Run test against

        /*val ms: SmithyIdlModelSerializer = SmithyIdlModelSerializer.builder().build()
        val node = ms.serialize(model1)
        println(node.values.first().toString())*/
    }

    // Produce the generated service code given model inputs.
    private fun generateSdk(model: Model, applicationProtocolFactory: () -> ApplicationProtocol): List<SourceFile> {
        val sourceFiles = mutableListOf<SourceFile>()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val serviceWriter = KotlinWriter("test")
        val defaultClientWriter = KotlinWriter("test")
        val service = model.expectShape(ShapeId.from("com.test#Example")).asServiceShape().get()
        val protocolGenerator = TestProtocolGenerator()
        val manifest = MockManifest()
        val delegator = KotlinDelegator(model.defaultSettings(), model, manifest, provider, listOf())
        val ctx = ProtocolGenerator.GenerationContext(
            model.defaultSettings(),
            model,
            service,
            provider,
            listOf(),
            RestJson1Trait.ID,
            delegator
        )

        val serviceGenerator = ServiceGenerator(model, provider, serviceWriter, service, "test", applicationProtocolFactory())
        serviceGenerator.render()

        val defaultClientGenerator = HttpProtocolClientGenerator(model, provider, defaultClientWriter, service, "test", listOf())
        defaultClientGenerator.render()

        // Models
        listOf("GetFooRequest")
            .map { it to model.expectShape(ShapeId.from("com.test#$it")).asStructureShape().get() }
            .forEach { (structureName, structureShape) ->
                val structureWriter = KotlinWriter("test.model")
                val structGenerator = StructureGenerator(model, provider, structureWriter, structureShape, protocolGenerator)
                structGenerator.render()

                sourceFiles.add(SourceFile.kotlin("$structureName.kt", structureWriter.toString()))
            }

        // Serializers
        protocolGenerator.generateSerializers(ctx)
        delegator.flushWriters()
        sourceFiles.add(SourceFile.kotlin("GetFooNoInputSerializer.kt", manifest.expectFileString("/src/main/kotlin/test/transform/GetFooNoInputSerializer.kt")))

        sourceFiles.add(SourceFile.kotlin("ExampleClient.kt", serviceWriter.toString()))
        sourceFiles.add(SourceFile.kotlin("DefaultExampleClient.kt", defaultClientWriter.toString()))

        return sourceFiles
    }
}

class TestProtocolGenerator(
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS,
    override val defaultContentType: String = "application/json",
    override val protocol: ShapeId = RestJson1Trait.ID
) : HttpBindingProtocolGenerator() {

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        TODO("Not yet implemented")
    }
}