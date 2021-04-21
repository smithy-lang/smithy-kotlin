package software.amazon.smithy.kotlin.codegen.test

import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.model.OperationNormalizer
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import java.net.URL

/**
 * This file houses classes and functions to help with testing with Smithy models.
 *
 * These functions should be relatively low-level and deal directly with types provided
 * by smithy-codegen.
 */

// attempt to replicate transforms that happen in CodegenVisitor such that tests
// more closely reflect reality
private fun Model.applyKotlinCodegenTransforms(serviceShapeId: String?): Model {
    val serviceId = if (serviceShapeId != null) ShapeId.from(serviceShapeId) else {
        // try to autodiscover the service so that tests "Just Work" (TM) without having to do anything
        val services = this.shapes<ServiceShape>()
        check(services.size <= 1) { "multiple services discovered in model; auto inference of service shape impossible for test. Fix by passing the service shape explicitly" }
        if (services.isEmpty()) return this // no services defined, move along
        services.first().id
    }

    val transforms = listOf(OperationNormalizer::transform)
    return transforms.fold(this) { m, transform ->
        transform(m, serviceId)
    }
}

/**
 * Load and initialize a model from a Java resource URL
 */
fun URL.asSmithy(serviceShapeId: String? = null): Model {
    val model = Model.assembler()
        .addImport(this)
        .discoverModels()
        .assemble()
        .unwrap()

    return model.applyKotlinCodegenTransforms(serviceShapeId)
}

/**
 * Load and initialize a model from a String
 */
fun String.asSmithyModel(sourceLocation: String? = null, serviceShapeId: String? = null, applyDefaultTransforms: Boolean = true): Model {
    val processed = if (this.startsWith("\$version")) this else "\$version: \"1.0\"\n$this"
    val model = Model.assembler()
        .discoverModels()
        .addUnparsedModel(sourceLocation ?: "test.smithy", processed)
        .assemble()
        .unwrap()
    return if (applyDefaultTransforms) model.applyKotlinCodegenTransforms(serviceShapeId) else model
}

/**
 * Generate Smithy IDL from a model instance.
 *
 * NOTE: this is used for debugging / unit test generation, please don't remove.
 */
fun Model.toSmithyIDL(): String {
    val builtInModelIds = setOf("smithy.test.smithy", "aws.auth.smithy", "aws.protocols.smithy", "aws.api.smithy")
    val ms: SmithyIdlModelSerializer = SmithyIdlModelSerializer.builder().build()
    val node = ms.serialize(this)

    return node.filterNot { builtInModelIds.contains(it.key.toString()) }.values.first()
}

/**
 * Initiate codegen for the model and produce a [TestContext].
 *
 * @param serviceShapeId the smithy Id for the service to codegen, defaults to "com.test#Example"
 */
fun Model.newTestContext(
    serviceShapeId: String = "com.test#Example",
    settings: KotlinSettings = this.defaultSettings(),
    generator: ProtocolGenerator = MockHttpProtocolGenerator()
): TestContext {
    val manifest = MockManifest()
    val serviceShapeName = serviceShapeId.split("#")[1]
    val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(this, "test", serviceShapeName)
    val service = this.getShape(ShapeId.from(serviceShapeId)).get().asServiceShape().get()
    val delegator = KotlinDelegator(settings, this, manifest, provider)

    val ctx = ProtocolGenerator.GenerationContext(
        settings,
        this,
        service,
        provider,
        listOf(),
        generator.protocol,
        delegator
    )
    return TestContext(ctx, manifest, generator)
}

/**
 * Generate a KotlinSettings instance from a model.
 * @param packageName name of module or "test" if unspecified
 * @param packageVersion version of module or "1.0.0" if unspecified
 *
 * Example:
 *  {
 *  	"service": "com.amazonaws.lambda#AWSGirApiService",
 *  	"package": {
 *  		"name": "aws.sdk.kotlin.services.lambda",
 *  		"version": "0.2.0-SNAPSHOT",
 *  		"description": "AWS Lambda"
 *  	},
 *  	"sdkId": "Lambda",
 *  	"build": {
 *  		"generateDefaultBuildFiles": false
 *  	}
 *  }
 */
internal fun Model.defaultSettings(
    serviceName: String = "test#service",
    packageName: String = "test",
    packageVersion: String = "1.0.0",
    sdkId: String = "Test",
    generateDefaultBuildFiles: Boolean = false
): KotlinSettings = KotlinSettings.from(
    this,
    Node.objectNodeBuilder()
        .withMember("service", Node.from(serviceName))
        .withMember(
            "package",
            Node.objectNode()
                .withMember("name", Node.from(packageName))
                .withMember("version", Node.from(packageVersion))
        )
        .withMember("sdkId", Node.from(sdkId))
        .withMember(
            "build",
            Node.objectNode()
                .withMember("generateDefaultBuildFiles", Node.from(generateDefaultBuildFiles)))
        .build()
)

fun String.generateTestModel(
    protocol: String,
    namespace: String = "com.test",
    serviceName: String = "Example",
    operations: List<String> = listOf("Foo")
): Model {
    val completeModel = """
        namespace $namespace

        use aws.protocols#$protocol

        @$protocol
        service $serviceName {
            version: "1.0.0",
            operations: [
                ${operations.joinToString(separator = ", ")}
            ]
        }
        
        $this
    """.trimIndent()

    return completeModel.asSmithyModel()
}

// Produce a GenerationContext given a model, it's expected namespace and service name.
fun Model.generateTestContext(namespace: String, serviceName: String): ProtocolGenerator.GenerationContext {
    val packageNode = Node.objectNode().withMember("name", Node.from("test"))
        .withMember("version", Node.from("1.0.0"))

    val settings = KotlinSettings.from(
        this,
        Node.objectNodeBuilder()
            .withMember("package", packageNode)
            .build()
    )
    val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(this, namespace, serviceName)
    val service = this.expectShape<ServiceShape>("$namespace#$serviceName")
    val generator: ProtocolGenerator = MockHttpProtocolGenerator()
    val manifest = MockManifest()
    val delegator = KotlinDelegator(settings, this, manifest, provider)

    return ProtocolGenerator.GenerationContext(
        settings,
        this,
        service,
        provider,
        listOf(),
        generator.protocol,
        delegator
    )
}