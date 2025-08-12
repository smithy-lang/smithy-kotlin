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

internal fun generateCborServiceTest() {
    val modelPath: Path = Paths.get("model", "service-cbor-test.smithy")
    val defaultModel = ModelAssembler()
        .discoverModels()
        .addImport(modelPath)
        .assemble()
        .unwrap()
    val serviceName = "CborServiceTest"
    val packageName = "com.cbor"
    val outputDirName = "service-cbor-test"

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

    val postTestOperation = """
        package $packageName.operations

        import $packageName.model.PostTestRequest
        import $packageName.model.PostTestResponse

        public fun handlePostTestRequest(req: PostTestRequest): PostTestResponse {
            val response = PostTestResponse.Builder()
            val input1 = req.input1 ?: ""
            val input2 = req.input2 ?: 0
            response.output1 = input1 + " world!"
            response.output2 = input2 + 1
            return response.build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/PostTestOperation.kt", postTestOperation)

    val errorTestOperation = """
        package $packageName.operations

        import $packageName.model.ErrorTestRequest
        import $packageName.model.ErrorTestResponse

        public fun handleErrorTestRequest(req: ErrorTestRequest): ErrorTestResponse {
            val variable: String? = null
            val error = variable!!.length
            return ErrorTestResponse.Builder().build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/ErrorTestOperation.kt", errorTestOperation)

    val httpErrorTestOperation = """
        package $packageName.operations

        import $packageName.model.HttpErrorTestRequest
        import $packageName.model.HttpErrorTestResponse
        import $packageName.model.HttpError

        public fun handleHttpErrorTestRequest(req: HttpErrorTestRequest): HttpErrorTestResponse {
            
            val error = HttpError.Builder()
            error.msg = "this is an error message"
            error.num = 444
            throw error.build()
            
            return HttpErrorTestResponse.Builder().build()
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/operations/HttpErrorTestOperation.kt", httpErrorTestOperation)

    val bearerValidation = """
        package $packageName.auth

        public fun bearerValidation(token: String): UserPrincipal? {
            if (token == "correctToken") return UserPrincipal("Authenticated User") else return null
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/auth/Validation.kt", bearerValidation)

    val settingGradleKts = """
        rootProject.name = "service-cbor-test"
        includeBuild("../../../../../")
    """.trimIndent()
    manifest.writeFile("settings.gradle.kts", settingGradleKts)
}
