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
        
        internal object BearerValidation {
            public fun bearerValidation(token: String): UserPrincipal? {
                // TODO: implement me
                if (token == "correctToken") return UserPrincipal("Authenticated User") else return null
            }
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/auth/Validation.kt", bearerValidation)

    val AWSValidation = """
        package $packageName.auth
        
        import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
        
        internal object SigV4CredentialStore {
            private val table: Map<String, Credentials> = mapOf(
                "AKIAIOSFODNN7EXAMPLE" to Credentials(accessKeyId = "AKIAIOSFODNN7EXAMPLE", secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"),
                "EXAMPLEACCESSKEY1234" to Credentials(accessKeyId = "EXAMPLEACCESSKEY1234", secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"),
            )
            internal fun get(accessKeyId: String): Credentials? {
                // TODO: implement me: return Credentials(accessKeyId = ..., secretAccessKey = ...)
                return table[accessKeyId]
            }
        }
        
        internal object SigV4aPublicKeyStore {
            private val table: MutableMap<String, java.security.PublicKey> = mutableMapOf()
        
            init {
                val pem = ""${'"'}
                    -----BEGIN PUBLIC KEY-----
                    MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4BB0k4K89eCESVtC39Kzm0HA+lYx
                    8YF3OZDop7htXAyhGAXn4U70ViNmtG+eWu2bQOXGEIMtoBAEoRk11WXOAw==
                    -----END PUBLIC KEY-----
                ""${'"'}.trimIndent()
                val clean = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), "")
                val keyBytes = java.util.Base64.getDecoder().decode(clean)
                val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
                val kf = java.security.KeyFactory.getInstance("EC")
                table["EXAMPLEACCESSKEY1234"] = kf.generatePublic(spec)
            }
        
            internal fun get(accessKeyId: String): java.security.PublicKey? {
                return table[accessKeyId]
            }
        }
    """.trimIndent()
    manifest.writeFile("src/main/kotlin/$packagePath/auth/AWSValidation.kt", AWSValidation)

    val settingGradleKts = """
        rootProject.name = "service-json-test"
        includeBuild("../../../../../")
    """.trimIndent()
    manifest.writeFile("settings.gradle.kts", settingGradleKts)
}
