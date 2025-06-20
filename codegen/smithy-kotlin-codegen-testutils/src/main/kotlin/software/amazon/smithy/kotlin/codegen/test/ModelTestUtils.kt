/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.test

import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.OperationNormalizer
import software.amazon.smithy.kotlin.codegen.model.shapes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex.CheckMode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import software.amazon.smithy.model.validation.ValidatedResultException
import java.net.URL

// This file houses classes and functions to help with testing with Smithy models.
// These functions should be relatively low-level and deal directly with types provided by smithy-codegen.

/**
 * Unless necessary to deviate for test reasons, the following literals should be used in test models:
 *  smithy version: "2"
 *  model version: "2.0.0"
 *  namespace: TestDefault.NAMESPACE
 *  service name: "Test"
 */
object TestModelDefault {
    const val SMITHY_IDL_VERSION = "2"
    const val MODEL_VERSION = "2.0.0"
    const val NAMESPACE = "com.test"
    const val SERVICE_NAME = "Test"
    const val SDK_ID = "Test"
    const val SERVICE_SHAPE_ID = "com.test#Test"
}

// attempt to replicate transforms that happen in CodegenVisitor such that tests
// more closely reflect reality
private fun Model.applyKotlinCodegenTransforms(serviceShapeId: String?): Model {
    val serviceId = serviceShapeId?.let(ShapeId::from) ?: run {
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
fun URL.toSmithyModel(serviceShapeId: String? = null): Model {
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
fun String.toSmithyModel(sourceLocation: String? = null, serviceShapeId: String? = null, applyDefaultTransforms: Boolean = true): Model {
    val processed = if (this.trimStart().startsWith("\$version")) this else "\$version: \"1.0\"\n$this"
    val model = try {
        Model.assembler()
            .discoverModels()
            .addUnparsedModel(sourceLocation ?: "test.smithy", processed)
            .assemble()
            .unwrap()
    } catch (e: ValidatedResultException) {
        System.err.println("Model failed to parse:")
        System.err.println(this)
        throw e
    }
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
 * @param serviceName name of service without namespace
 * @param packageName root namespace of model
 * @param settings [KotlinSettings] associated w/ test context
 * @param generator [ProtocolGenerator] associated w/ test context
 */
fun Model.newTestContext(
    serviceName: String = TestModelDefault.SERVICE_NAME,
    packageName: String = TestModelDefault.NAMESPACE,
    settings: KotlinSettings = defaultSettings(serviceName, packageName),
    generator: ProtocolGenerator = MockHttpProtocolGenerator(this),
    integrations: List<KotlinIntegration> = listOf(),
): TestContext {
    val manifest = MockManifest()
    val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model = this, rootNamespace = packageName, serviceName = serviceName, settings = settings)
    val service = this.getShape(ShapeId.from("$packageName#$serviceName")).get().asServiceShape().get()
    val delegator = KotlinDelegator(settings, this, manifest, provider, integrations)

    val ctx = ProtocolGenerator.GenerationContext(
        settings,
        this,
        service,
        provider,
        integrations,
        generator.protocol,
        delegator,
    )
    return TestContext(ctx, manifest, generator)
}

fun TestContext.toCodegenContext() = object : CodegenContext {
    override val model: Model = generationCtx.model
    override val symbolProvider: SymbolProvider = generationCtx.symbolProvider
    override val settings: KotlinSettings = generationCtx.settings
    override val protocolGenerator: ProtocolGenerator = generator
    override val integrations: List<KotlinIntegration> = generationCtx.integrations
}

/**
 * Generate a KotlinSettings instance from a model.
 * @param serviceName name of service without namespace or null to attempt to discover from model
 * @param packageName name of module or DEFAULT_SERVICE_NAME if unspecified
 * @param packageVersion version of module or DEFAULT_MODEL_VERSION if unspecified
 * @param sdkId sdk id of settings
 * @param generateDefaultBuildFiles flag used to determine what build files to generate
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
fun Model.defaultSettings(
    serviceName: String? = null,
    packageName: String = TestModelDefault.NAMESPACE,
    packageVersion: String = TestModelDefault.MODEL_VERSION,
    sdkId: String = TestModelDefault.SDK_ID,
    generateDefaultBuildFiles: Boolean = false,
    nullabilityCheckMode: CheckMode = CheckMode.CLIENT_CAREFUL,
    defaultValueSerializationMode: DefaultValueSerializationMode = DefaultValueSerializationMode.DEFAULT,
): KotlinSettings {
    val serviceId = if (serviceName == null) {
        this.inferService()
    } else {
        this.getShape(ShapeId.from("$packageName#$serviceName")).getOrNull()?.id
            ?: error("Unable to find service '$serviceName' in model.")
    }

    return KotlinSettings.from(
        this,
        Node.objectNodeBuilder()
            .withMember("service", Node.from(serviceId.toString()))
            .withMember(
                "package",
                Node.objectNode()
                    .withMember("name", Node.from(packageName))
                    .withMember("version", Node.from(packageVersion)),
            )
            .withMember("sdkId", Node.from(sdkId))
            .withMember(
                "build",
                Node.objectNode()
                    .withMember("generateDefaultBuildFiles", Node.from(generateDefaultBuildFiles)),
            )
            .withMember(
                "api",
                Node.objectNode()
                    .withMember(ApiSettings.NULLABILITY_CHECK_MODE, Node.from(nullabilityCheckMode.kotlinPluginSetting))
                    .withMember(ApiSettings.DEFAULT_VALUE_SERIALIZATION_MODE, Node.from(defaultValueSerializationMode.value)),
            )
            .build(),
    )
}

/** Generate a Smithy IDL model based on input parameters and source string */
fun String.generateTestModel(
    protocol: String,
    namespace: String = TestModelDefault.NAMESPACE,
    serviceName: String = TestModelDefault.SERVICE_NAME,
    operations: List<String>,
): Model {
    val completeModel = """
        namespace $namespace

        use aws.protocols#$protocol

        @$protocol
        service ${serviceName.filter { !it.isWhitespace() }} {
            version: "${TestModelDefault.MODEL_VERSION}",
            operations: [
                ${operations.joinToString(separator = ", ")}
            ]
        }
        
        $this
    """.trimIndent()

    return completeModel.toSmithyModel()
}

// Specifies AWS protocols that can be set on test models.
enum class AwsProtocolModelDeclaration(val annotation: String, val import: String) {
    REST_JSON("@restJson1", "aws.protocols#restJson1"),
    AWS_JSON_1_1("@awsJson1_1", "aws.protocols#awsJson1_1"),
}

// Generates the model header which by default conforms to the conventions defined for test models.
fun String.prependNamespaceAndService(
    version: String = TestModelDefault.SMITHY_IDL_VERSION,
    namespace: String = TestModelDefault.NAMESPACE,
    imports: List<String> = emptyList(),
    serviceName: String = TestModelDefault.SERVICE_NAME,
    protocol: AwsProtocolModelDeclaration? = null,
    operations: List<String> = emptyList(),
): String {
    val versionExpr = "\$version: \"$version\""
    val (modelProtocol, modelImports) = if (protocol == null) {
        "" to imports
    } else {
        protocol.annotation to imports + listOf(protocol.import)
    }

    val importExpr = modelImports
        .map { "use $it" }
        .plus("use aws.api#service")
        .joinToString(separator = "\n")

    return (
        """
        $versionExpr
        namespace $namespace
        $importExpr
        $modelProtocol
        @service(sdkId: "$serviceName")
        service ${serviceName.filter { !it.isWhitespace() }} { 
            version: "${TestModelDefault.MODEL_VERSION}",
            operations: $operations
        }
        

        """.trimIndent() + this.trimIndent()
        )
}
