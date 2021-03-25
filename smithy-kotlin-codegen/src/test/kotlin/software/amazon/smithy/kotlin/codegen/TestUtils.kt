/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.HttpBindingResolver
import software.amazon.smithy.kotlin.codegen.integration.HttpFeature
import software.amazon.smithy.kotlin.codegen.integration.HttpProtocolClientGenerator
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.model.OperationNormalizer
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.*
import java.net.URL

fun String.shouldSyntacticSanityCheck() {
    // sanity check since we are testing fragments
    var openBraces = 0
    var closedBraces = 0
    var openParens = 0
    var closedParens = 0
    this.forEach {
        when (it) {
            '{' -> openBraces++
            '}' -> closedBraces++
            '(' -> openParens++
            ')' -> closedParens++
        }
    }
    Assertions.assertEquals(openBraces, closedBraces, "unmatched open/closed braces:\n$this")
    Assertions.assertEquals(openParens, closedParens, "unmatched open/close parens:\n$this")
}

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
 * Container for type instances necessary for tests
 */
data class TestContext(
    val generationCtx: ProtocolGenerator.GenerationContext,
    val manifest: MockManifest,
    val generator: ProtocolGenerator
)

// Convenience function to retrieve a shape from a [TestContext]
fun TestContext.expectShape(shapeId: String): Shape =
    this.generationCtx.model.expectShape(ShapeId.from(shapeId))

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
 */
internal fun Model.defaultSettings(
    serviceName: String = "test#service",
    packageName: String = "test",
    packageVersion: String = "1.0.0"
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
        .build()
)

// Execute the codegen and return the generated output
fun testRender(
    members: List<MemberShape>,
    renderFn: (List<MemberShape>, KotlinWriter) -> Unit
): String {
    val writer = KotlinWriter("test")
    renderFn(members, writer)
    return writer.toString()
}

// Retrieves Response Document members for HttpTrait-enabled protocols
fun TestContext.responseMembers(shape: Shape): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getResponseBindings(shape)

    return responseBindings.values
        .filter { it.location == HttpBinding.Location.DOCUMENT }
        .sortedBy { it.memberName }
        .map { it.member }
}

// Retrieves Request Document members for HttpTrait-enabled protocols
fun TestContext.requestMembers(shape: Shape): List<MemberShape> {
    val bindingIndex = HttpBindingIndex.of(this.generationCtx.model)
    val responseBindings = bindingIndex.getRequestBindings(shape)

    return responseBindings.values
        .filter { it.location == HttpBinding.Location.DOCUMENT }
        .sortedBy { it.memberName }
        .map { it.member }
}

fun MockManifest.getTransformFileContents(filename: String): String {
    return expectFileString("src/main/kotlin/test/transform/$filename")
}

// Will generate an IDE diff in the case of a test assertion failure.
fun String?.shouldContainOnlyOnceWithDiff(expected: String) {
    try {
        this.shouldContainOnlyOnce(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

fun String?.shouldContainWithDiff(expected: String) {
    try {
        this.shouldContain(expected)
    } catch (originalException: AssertionError) {
        kotlin.test.assertEquals(expected, this) // no need to rethrow as this will throw
    }
}

fun TestContext.toGenerationContext(): GenerationContext =
    GenerationContext(generationCtx.model, generationCtx.symbolProvider, generationCtx.settings, generator)

fun <T : Shape> TestContext.toRenderingContext(writer: KotlinWriter, forShape: T? = null): RenderingContext<T> =
    toGenerationContext().toRenderingContext(writer, forShape)

// Format a multi-line string suitable for comparison with codegen, defaults to one level of indention.
fun String.formatForTest(indent: String = "    ") =
    trimIndent()
        .prependIndent(indent)
        .split('\n')
        .map { if (it.isBlank()) "" else it }
        .joinToString(separator = "\n") { it }

class TestProtocolClientGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    features: List<HttpFeature>,
    httpBindingResolver: HttpBindingResolver
) : HttpProtocolClientGenerator(ctx, features, httpBindingResolver) {
    override val serdeProviderSymbol: Symbol = buildSymbol {
        name = "JsonSerdeProvider"
        namespace(KotlinDependency.CLIENT_RT_SERDE_JSON)
    }
}
