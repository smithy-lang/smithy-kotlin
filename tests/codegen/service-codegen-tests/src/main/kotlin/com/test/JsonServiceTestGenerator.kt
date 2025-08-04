package com.test

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal fun generateJsonServiceTest() {
    val modelPath: Path = Paths.get("model", "service-json-test.smithy")
    val defaultModel = ModelAssembler()
        .discoverModels()
        .addImport(modelPath)
        .assemble()
        .unwrap()
    val serviceName = "JsonServiceTest"
    val packageName = "com.json"
    val outputDirName = "service-json-test"

    val packagePath = packageName.replace('.', '/')

    val settings: ObjectNode = ObjectNode.builder()
        .withMember("service", Node.from("$packageName#$serviceName"))
        .withMember(
            "package",
            ObjectNode.builder()
                .withMember("name", Node.from(packageName))
                .withMember("version", Node.from("1.0.0"))
                .build(),
        )
        .withMember(
            "build",
            ObjectNode.builder()
                .withMember("rootProject", true)
                .withMember("generateServiceProject", true)
                .withMember(
                    "optInAnnotations",
                    Node.arrayNode(
                        Node.from("aws.smithy.kotlin.runtime.InternalApi"),
                        Node.from("kotlinx.serialization.ExperimentalSerializationApi"),
                    ),
                )
                .build(),
        )
        .withMember(
            "serviceStub",
            ObjectNode.builder().withMember("framework", Node.from("ktor")).build(),
        )
        .build()
    val outputDir: Path = Paths.get("build", outputDirName).also { Files.createDirectories(it) }
    val manifest: FileManifest = FileManifest.create(outputDir)

    val context: PluginContext = PluginContext.builder()
        .model(defaultModel)
        .fileManifest(manifest)
        .settings(settings)
        .build()
    KotlinCodegenPlugin().execute(context)

    val httpHeaderTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpHeaderTestRequest
        import $packageName.model.HttpHeaderTestResponse

        public fun handleHttpHeaderTestRequest(req: HttpHeaderTestRequest): HttpHeaderTestResponse {
            val response = HttpHeaderTestResponse.Builder()
            response.header = req.headers?.get("hhh")
            response.headers = mapOf("hhh" to (req.header ?: ""))
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpHeaderTestOperation.kt", httpHeaderTestOperation)

    val httpLabelTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpLabelTestRequest
        import $packageName.model.HttpLabelTestResponse

        public fun handleHttpLabelTestRequest(req: HttpLabelTestRequest): HttpLabelTestResponse {
            val response = HttpLabelTestResponse.Builder()
            response.output = req.foo
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpLabelTestOperation.kt", httpLabelTestOperation)

    val httpQueryTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpQueryTestRequest
        import $packageName.model.HttpQueryTestResponse

        public fun handleHttpQueryTestRequest(req: HttpQueryTestRequest): HttpQueryTestResponse {
            val response = HttpQueryTestResponse.Builder()
            response.output = req.query.toString() + (req.params?.get("qqq") ?: "")
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpQueryTestOperation.kt", httpQueryTestOperation)

    val httpStringPayloadTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpStringPayloadTestRequest
        import $packageName.model.HttpStringPayloadTestResponse

        public fun handleHttpStringPayloadTestRequest(req: HttpStringPayloadTestRequest): HttpStringPayloadTestResponse {
            val response = HttpStringPayloadTestResponse.Builder()
            response.content = req.content
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpStringPayloadTestOperation.kt", httpStringPayloadTestOperation)

    val httpStructurePayloadTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpStructurePayloadTestRequest
        import $packageName.model.HttpStructurePayloadTestResponse
        import $packageName.model.HttpStructurePayloadTestStructure

        public fun handleHttpStructurePayloadTestRequest(req: HttpStructurePayloadTestRequest): HttpStructurePayloadTestResponse {
            val response = HttpStructurePayloadTestResponse.Builder()
            val content = HttpStructurePayloadTestStructure.Builder()
            content.content1 = req.content?.content1
            content.content2 = req.content?.content2
            content.content3 = req.content?.content3
            response.content = content.build()
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpStructurePayloadTestOperation.kt", httpStructurePayloadTestOperation)

    val timestampTestOperation = """
        package $packageName.operations

        import $packageName.model.TimestampTestRequest
        import $packageName.model.TimestampTestResponse

        public fun handleTimestampTestRequest(req: TimestampTestRequest): TimestampTestResponse {
            val response = TimestampTestResponse.Builder()
            response.default = req.default
            response.dateTime = req.dateTime
            response.httpDate = req.httpDate
            response.epochSeconds = req.epochSeconds
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/TimestampTestOperation.kt", timestampTestOperation)

    val jsonNameTestOperation = """
        package $packageName.operations

        import $packageName.model.JsonNameTestRequest
        import $packageName.model.JsonNameTestResponse

        public fun handleJsonNameTestRequest(req: JsonNameTestRequest): JsonNameTestResponse {
            val response = JsonNameTestResponse.Builder()
            response.content = req.content
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/JsonNameTestOperation.kt", jsonNameTestOperation)

    val bearerValidation = """
        package $packageName.auth

        public fun bearerValidation(token: String): UserPrincipal? {
            if (token == "correctToken") return UserPrincipal("Authenticated User") else return null
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/auth/Validation.kt", bearerValidation)

    val settingGradleKts = """
        rootProject.name = "service-json-test"
        includeBuild("../../../../../")
    """.trimIndent()
    manifest.writeFile("settings.gradle.kts", settingGradleKts)
}
