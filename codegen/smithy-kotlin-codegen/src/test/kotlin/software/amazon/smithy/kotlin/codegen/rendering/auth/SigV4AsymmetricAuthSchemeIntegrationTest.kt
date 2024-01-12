package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinSymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigV4AsymmetricAuthSchemeIntegrationTest {
    private val testModelWithoutSigV4aTrait = """
            namespace smithy.example

            use aws.protocols#awsJson1_0
            use aws.auth#sigv4

            @awsJson1_0
            @sigv4(name: "example-signing-name")
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            operation GetFoo {
                input: GetFooInput
            }
            
            operation GetNotFoo {
                input: GetFooInput
            }
            
            structure GetFooInput {
                payload: String
            }            
        """.toSmithyModel()

    private val testModelWithSigV4aTrait = """
            namespace smithy.example

            use aws.protocols#awsJson1_0
            use aws.auth#sigv4
            use aws.auth#sigv4a

            @awsJson1_0
            @sigv4(name: "example-signing-name")
            @sigv4a(name: "example-signing-name")
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            operation GetFoo {
                input: GetFooInput
            }
            
            operation GetNotFoo {
                input: GetFooInput
            }
            
            structure GetFooInput {
                payload: String
            }            
        """.toSmithyModel()

    @Test
    fun testModelWithoutSigV4aTrait() {
        val settings = KotlinSettings(
            testModelWithoutSigV4aTrait.serviceShapes.first().id,
            KotlinSettings.PackageSettings("example", "1.0.0"),
            testModelWithoutSigV4aTrait.serviceShapes.first().id.toString(),
        )

        val symbolProvider = KotlinSymbolProvider(
            testModelWithoutSigV4aTrait,
            settings,
        )

        val generationContext: ProtocolGenerator.GenerationContext = ProtocolGenerator.GenerationContext(
            settings,
            testModelWithoutSigV4aTrait,
            testModelWithoutSigV4aTrait.serviceShapes.first(),
            symbolProvider,
            emptyList(),
            ShapeId.from(AwsJson1_0Trait.ID.toString()),
            KotlinDelegator(
                settings,
                testModelWithoutSigV4aTrait,
                MockManifest(),
                symbolProvider,
            ),
        )

        val codegenContext = object : CodegenContext {
            override val model: Model = testModelWithoutSigV4aTrait
            override val symbolProvider: SymbolProvider = symbolProvider
            override val settings: KotlinSettings = settings
            override val protocolGenerator: ProtocolGenerator? = null
            override val integrations: List<KotlinIntegration> = listOf()
        }

        assertFalse(modelHasSigV4aTrait(codegenContext))
        assertFalse(modelHasSigV4aTrait(generationContext))
    }

    @Test
    fun testModelWithSigV4aTrait() {
        val settings = KotlinSettings(
            testModelWithSigV4aTrait.serviceShapes.first().id,
            KotlinSettings.PackageSettings("example", "1.0.0"),
            testModelWithSigV4aTrait.serviceShapes.first().id.toString(),
        )

        val symbolProvider = KotlinSymbolProvider(
            testModelWithSigV4aTrait,
            settings,
        )

        val generationContext: ProtocolGenerator.GenerationContext = ProtocolGenerator.GenerationContext(
            settings,
            testModelWithSigV4aTrait,
            testModelWithSigV4aTrait.serviceShapes.first(),
            symbolProvider,
            emptyList(),
            ShapeId.from(AwsJson1_0Trait.ID.toString()),
            KotlinDelegator(
                settings,
                testModelWithSigV4aTrait,
                MockManifest(),
                symbolProvider,
            ),
        )

        val codegenContext = object : CodegenContext {
            override val model: Model = testModelWithSigV4aTrait
            override val symbolProvider: SymbolProvider = symbolProvider
            override val settings: KotlinSettings = settings
            override val protocolGenerator: ProtocolGenerator? = null
            override val integrations: List<KotlinIntegration> = listOf()
        }

        assertTrue(modelHasSigV4aTrait(codegenContext))
        assertTrue(modelHasSigV4aTrait(generationContext))
    }
}