package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.InlineCodeWriterFormatter
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.closeAndOpenBlock
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.service.MediaType.ANY
import software.amazon.smithy.kotlin.codegen.service.MediaType.JSON
import software.amazon.smithy.kotlin.codegen.service.MediaType.OCTET_STREAM
import software.amazon.smithy.kotlin.codegen.service.MediaType.PLAIN_TEXT
import software.amazon.smithy.kotlin.codegen.service.contraints.ConstraintGenerator
import software.amazon.smithy.kotlin.codegen.service.contraints.ConstraintUtilsGenerator
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
import software.amazon.smithy.model.traits.HttpErrorTrait
import software.amazon.smithy.model.traits.HttpHeaderTrait
import software.amazon.smithy.model.traits.HttpLabelTrait
import software.amazon.smithy.model.traits.HttpPrefixHeadersTrait
import software.amazon.smithy.model.traits.HttpQueryParamsTrait
import software.amazon.smithy.model.traits.HttpQueryTrait
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.MediaTypeTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.AbstractCodeWriter

class LoggingWriter(parent: LoggingWriter? = null) : AbstractCodeWriter<LoggingWriter>() {
    init {
        trimBlankLines(parent?.trimBlankLines ?: 1)
        trimTrailingSpaces(parent?.trimTrailingSpaces ?: true)
        indentText = parent?.indentText ?: "    "
        expressionStart = parent?.expressionStart ?: '#'
        putFormatter('W', InlineCodeWriterFormatter(::LoggingWriter))
    }
}

internal class KtorStubGenerator(
    ctx: GenerationContext,
    delegator: KotlinDelegator,
    fileManifest: FileManifest,
) : AbstractStubGenerator(ctx, delegator, fileManifest) {

    override fun renderServerFrameworkImplementation(writer: KotlinWriter) {
        writer.withBlock("internal fun #T.module(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
            write("#T()", ServiceTypes(pkgName).configureLogging)
            withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerBodyLimit.RequestBodyLimit) {
                write("bodyLimit { #T.requestBodyLimit }", ServiceTypes(pkgName).serviceFrameworkConfig)
            }
            write("#T(#T)", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerDoubleReceive.DoubleReceive)
            write("#T()", ServiceTypes(pkgName).configureErrorHandling)
            write("#T()", ServiceTypes(pkgName).configureAuthentication)
            write("#T()", ServiceTypes(pkgName).configureRouting)
        }
            .write("")
        writer.withBlock("internal class KtorServiceFramework() : ServiceFramework {", "}") {
            write("private var engine: #T<*, *>? = null", RuntimeTypes.KtorServerCore.EmbeddedServerType)
            write("")
            write("private val engineFactory = #T.engine.toEngineFactory()", ServiceTypes(pkgName).serviceFrameworkConfig)

            write("")
            withBlock("override fun start() {", "}") {
                withInlineBlock("engine = #T(", ")", RuntimeTypes.KtorServerCore.embeddedServer) {
                    write("engineFactory,")
                    withBlock("configure = {", "}") {
                        withBlock("#T {", "}", RuntimeTypes.KtorServerCore.connector) {
                            write("host = #S", "0.0.0.0")
                            write("port = #T.port", ServiceTypes(pkgName).serviceFrameworkConfig)
                        }
                        withBlock("when (this) {", "}") {
                            withBlock("is #T -> {", "}", RuntimeTypes.KtorServerNetty.Configuration) {
                                write("requestReadTimeoutSeconds = #T.requestReadTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                                write("responseWriteTimeoutSeconds = #T.responseWriteTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                            }

                            withBlock("is #T -> {", "}", RuntimeTypes.KtorServerCio.Configuration) {
                                write("connectionIdleTimeoutSeconds = #T.requestReadTimeoutSeconds", ServiceTypes(pkgName).serviceFrameworkConfig)
                            }

                            withBlock("is #T -> {", "}", RuntimeTypes.KtorServerJettyJakarta.Configuration) {
                                write(
                                    "idleTimeout = #T.requestReadTimeoutSeconds.#T",
                                    ServiceTypes(pkgName).serviceFrameworkConfig,
                                    KotlinTypes.Time.seconds,
                                )
                            }
                        }
                    }
                }
                write("{ #T() }", ServiceTypes(pkgName).module)
                write("engine?.apply { start(wait = true) }")
            }
            write("")
            withBlock("final override fun close() {", "}") {
                write("engine?.stop(#T.closeGracePeriodMillis, #T.closeTimeoutMillis)", ServiceTypes(pkgName).serviceFrameworkConfig, ServiceTypes(pkgName).serviceFrameworkConfig)
                write("engine = null")
            }
        }
    }

    override fun renderUtils() {
        renderLogging()
    }

    private fun renderLogging() {
        delegator.useFileWriter("Logging.kt", "$pkgName.utils") { writer ->

            writer.withBlock("internal fun #T.configureLogging() {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock(
                    "val slf4jLevel: #T? = when (#T.logLevel) {",
                    "}",
                    RuntimeTypes.KtorLoggingSlf4j.Level,
                    ServiceTypes(pkgName).serviceFrameworkConfig,
                ) {
                    write("#T.INFO -> #T.INFO", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("#T.TRACE -> #T.TRACE", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("#T.DEBUG -> #T.DEBUG", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("#T.WARN -> #T.WARN", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("#T.ERROR -> #T.ERROR", ServiceTypes(pkgName).logLevel, RuntimeTypes.KtorLoggingSlf4j.Level)
                    write("#T.OFF -> null", ServiceTypes(pkgName).logLevel)
                }
                write("")
                write("val logbackLevel = slf4jLevel?.let { #T.valueOf(it.name) } ?: #T.OFF", RuntimeTypes.KtorLoggingLogback.Level, RuntimeTypes.KtorLoggingLogback.Level)
                write("")
                write(
                    "(#T.getILoggerFactory() as #T).getLogger(#T).level = logbackLevel",
                    RuntimeTypes.KtorLoggingSlf4j.LoggerFactory,
                    RuntimeTypes.KtorLoggingLogback.LoggerContext,
                    RuntimeTypes.KtorLoggingSlf4j.ROOT_LOGGER_NAME,
                )
                write("")
                withBlock("if (slf4jLevel != null) {", "}") {
                    withBlock("#T(#T) {", "}", RuntimeTypes.KtorServerCore.install, RuntimeTypes.KtorServerLogging.CallLogging) {
                        write("level = slf4jLevel")
                        withBlock("format { call ->", "}") {
                            write("val status = call.response.status()")
                            write("\"\${call.request.#T.value} \${call.request.#T} → \$status\"", RuntimeTypes.KtorServerRouting.requestHttpMethod, RuntimeTypes.KtorServerRouting.requestUri)
                        }
                    }
                }
                write("val log = #T.getLogger(#S)", RuntimeTypes.KtorLoggingSlf4j.LoggerFactory, ctx.settings.pkg.name)

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStarting) {
                    write("log.info(#S)", "Server is starting...")
                }

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStarted) {
                    write("log.info(#S)", "Server started – ready to accept requests.")
                }

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopping) {
                    write("log.warn(#S)", "Server is stopping – waiting for in-flight requests...")
                }

                withBlock("monitor.subscribe(#T) {", "}", RuntimeTypes.KtorServerCore.ApplicationStopped) {
                    write("log.info(#S)", "Server stopped cleanly.")
                }
            }
        }
        val loggingWriter = LoggingWriter()
        loggingWriter.withBlock("<configuration>", "</configuration>") {
            withBlock("<appender name=#S class=#S>", "</appender>", "STDOUT", "ch.qos.logback.core.ConsoleAppender") {
                withBlock("<encoder>", "</encoder>") {
                    withBlock("<pattern>", "</pattern>") {
                        write("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level %logger{36} - %msg%n")
                    }
                }
            }
            withBlock("<root>", "</root>") {
                write("<appender-ref ref=#S/>", "STDOUT")
            }
        }
        val contents = loggingWriter.toString()
        fileManifest.writeFile("src/main/resources/logback.xml", contents)
    }

    // Generates `Authentication.kt` with Authenticator interface + configureSecurity().
    override fun renderAuthModule() {
        delegator.useFileWriter("UserPrincipal.kt", "$pkgName.auth") { writer ->
            writer.withBlock("public data class UserPrincipal(", ")") {
                write("val user: String")
            }
        }

        delegator.useFileWriter("Validation.kt", "$pkgName.auth") { writer ->

            writer.withBlock("internal object BearerValidation {", "}") {
                withBlock("public fun bearerValidation(token: String): UserPrincipal? {", "}") {
                    write("// TODO: implement me")
                    write("if (true) return UserPrincipal(#S) else return null", "Authenticated User")
                }
            }
            writer.write("")
            writer.withBlock("internal object SigV4CredentialStore {", "}") {
                withBlock("private val table: Map<String, #T> = mapOf(", ")", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials) {
                    write("#S to #T(accessKeyId = #S, secretAccessKey = #S),", "AKIAIOSFODNN7EXAMPLE", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials, "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                    write("#S to #T(accessKeyId = #S, secretAccessKey = #S),", "EXAMPLEACCESSKEY1234", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials, "EXAMPLEACCESSKEY1234", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                }
                withBlock("internal fun get(accessKeyId: String): #T? {", "}", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials) {
                    write("// TODO: implement me: return Credentials(accessKeyId = ..., secretAccessKey = ...)")
                    write("return table[accessKeyId]")
                }
            }
            writer.write("")
            writer.withBlock("internal object SigV4aPublicKeyStore {", "}") {
                write("private val table: MutableMap<String, java.security.PublicKey> = mutableMapOf()")
                write("")
                withBlock("init {", "}") {
                    withBlock("val pem = \"\"\"", "\"\"\".trimIndent()") {
                        write("-----BEGIN PUBLIC KEY-----")
                        write("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4BB0k4K89eCESVtC39Kzm0HA+lYx")
                        write("8YF3OZDop7htXAyhGAXn4U70ViNmtG+eWu2bQOXGEIMtoBAEoRk11WXOAw==")
                        write("-----END PUBLIC KEY-----")
                    }
                    write(
                        "val clean = pem.replace(#S, #S).replace(#S, #S).replace(#S.toRegex(), #S)",
                        "-----BEGIN PUBLIC KEY-----",
                        "",
                        "-----END PUBLIC KEY-----",
                        "",
                        "\\s",
                        "",
                    )

                    write("val keyBytes = java.util.Base64.getDecoder().decode(clean)")
                    write("val spec = java.security.spec.X509EncodedKeySpec(keyBytes)")
                    write("val kf = java.security.KeyFactory.getInstance(#S)", "EC")
                    write("table[#S] = kf.generatePublic(spec)", "EXAMPLEACCESSKEY1234")
                }
                write("")
                withBlock("internal fun get(accessKeyId: String): java.security.PublicKey? {", "}") {
                    write("return table[accessKeyId]")
                }
            }
        }

        delegator.useFileWriter("SigV4.kt", "$pkgName.auth") { writer ->
            writer.withInlineBlock("internal fun #T.sigV4(", ")", RuntimeTypes.KtorServerAuth.AuthenticationConfig) {
                write("name: String = #S,", "aws-sigv4")
                write("configure: SigV4AuthProvider.Configuration.() -> Unit = {}")
            }
                .withBlock("{", "}") {
                    write("val provider = SigV4AuthProvider(SigV4AuthProvider.Configuration(name).apply(configure))")
                    write("register(provider)")
                }
                .write("")

            writer.withBlock("internal class SigV4AuthProvider(config: Configuration) : #T(config) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationProvider) {
                withBlock("internal class Configuration(name: String?) : #T.Config(name) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationProvider) {
                    write("var region: String = #S", "us-east-1")
                    write("var service: String = #S", "execute-api")
                    write("var clockSkew: #T = 5.#T", KotlinTypes.Time.Duration, KotlinTypes.Time.minutes)
                }
                write("")
                write("private val region = (config as Configuration).region")
                write("private val service = config.service")
                write("private val skew = config.clockSkew")
                write("")
                withBlock("override suspend fun onAuthenticate(context: #T) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationContext) {
                    write("val creds = verifySigV4(context.call, region, service, skew)")
                    withInlineBlock("if (creds == null) {", "}") {
                        withBlock("context.challenge(#S, #T.InvalidCredentials) { challenge, call ->", "}", "AWS4-HMAC-SHA256", RuntimeTypes.KtorServerAuth.AuthenticationFailedCause) {
                            write("call.#T(#T.Unauthorized, #S)", RuntimeTypes.KtorServerRouting.responseResponse, RuntimeTypes.KtorServerHttp.HttpStatusCode, "Unauthorized")
                            write("challenge.complete()")
                        }
                    }
                    withBlock(" else {", "}") {
                        write("context.principal(UserPrincipal(creds.accessKeyId))")
                    }
                }
            }
                .write("")

            writer.withInlineBlock("public suspend fun verifySigV4(", ")") {
                write("call: #T,", RuntimeTypes.KtorServerCore.ApplicationCallClass)
                write("region: String,")
                write("service: String,")
                write("maxClockSkew: #T", KotlinTypes.Time.Duration)
            }
                .withBlock(": #T? {", "}", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials) {
                    write("val authHeader = call.request.#T(#T.Authorization) ?: return null", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
                    write("if (!authHeader.startsWith(#S, ignoreCase = true)) return null", "AWS4-HMAC-SHA256")
                    write("")
                    write("fun part(name: String) = authHeader.substringAfter(#S).substringBefore(#S).trim()", "\$name=", ",")
                    write("")
                    write("val credential = part(#S) // accessKeyId/scope", "Credential")
                    write("val signedHeadersStr = part(#S)", "SignedHeaders")
                    write("val signatureHex = part(#S)", "Signature")
                    write("")
                    write("val signedHeaders: Set<String> = signedHeadersStr.split(';').map { it.trim().lowercase() }.toSet()")
                    write("if (#S !in signedHeaders) return null", "host")
                    write("if (!signedHeaders.any { it == #S || it == #S }) return null", "x-amz-date", "date")
                    write("val accessKeyId = credential.substringBefore(#S).takeIf { it.matches(Regex(#S)) } ?: return null", "/", "^[A-Z0-9]{16,128}$")
                    write("")
                    write("val scope = credential.substringAfter(#S, missingDelimiterValue = #S)", "/", "")
                    write("val parts = scope.split(#S)", "/")
                    write("if (parts.size != 4) return null")
                    write("val (yyyyMMdd, scopeRegion, scopeService, term) = parts")
                    write("if (scopeRegion != region || scopeService != service || term != #S) return null", "aws4_request")
                    write("if (!Regex(#S).matches(yyyyMMdd)) return null", "^\\d{8}$")
                    write("")
                    write("val rawXAmzDate = call.request.#T(#S)", RuntimeTypes.KtorServerRouting.requestHeader, "X-Amz-Date")
                    write("val rawHttpDate = call.request.#T(#T.Date)", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
                    withBlock("val signingInstant: #T = when {", "}", RuntimeTypes.Core.Instant) {
                        write("rawXAmzDate != null -> { try { #T.fromIso8601(rawXAmzDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
                        write("rawHttpDate != null -> { try { #T.fromRfc5322(rawHttpDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
                        write("else -> return null")
                    }
                    write("val scopeDate = signingInstant.format(#T.ISO_8601_CONDENSED_DATE)", RuntimeTypes.Core.TimestampFormat)
                    write("if (scopeDate != yyyyMMdd) return null")
                    write("")
                    write("val now = #T.now()", RuntimeTypes.Core.Instant)
                    write("if (signingInstant < now - maxClockSkew || signingInstant > now + maxClockSkew) return null")
                    write("")
                    write("val creds = SigV4CredentialStore.get(accessKeyId) ?: return null")
                    write("")
                    write("val secTokenHeaderName = #S", "x-amz-security-token")
                    write("val secToken = call.request.headers[secTokenHeaderName]")
                    withBlock("if (creds.sessionToken != null) {", "}") {
                        write("if (secToken == null || secToken != creds.sessionToken) return null")
                        write("if (secTokenHeaderName !in signedHeaders) return null")
                    }
                    write("")
                    write("val contentSha256 = call.request.headers[#S]", "x-amz-content-sha256")
                    write("val isUnsigned = contentSha256 == #S", "UNSIGNED-PAYLOAD")
                    write("")
                    write("val origin = call.request.local")
                    write("val payload: ByteArray = call.#T<ByteArray>()", RuntimeTypes.KtorServerRouting.requestReceive)
                    write("")
                    withBlock("val requestBuilder: #T = #T().apply {", "}", RuntimeTypes.Http.Request.HttpRequestBuilder, RuntimeTypes.Http.Request.HttpRequestBuilder) {
                        write("method = #T.parse(call.request.#T.value)", RuntimeTypes.Http.HttpMethod, RuntimeTypes.KtorServerRouting.requestHttpMethod)
                        write("")
                        write("val protoHeader = call.request.headers[#S] ?: origin.scheme", "X-Forwarded-Proto")
                        write("val isHttps = (protoHeader.equals(#S, ignoreCase = true))", "https")
                        write("val hostHeader = call.request.headers[#S] ?: call.request.headers[#S] ?: return null", "X-Forwarded-Host", "Host")
                        write("val hostOnly: String")
                        write("val portValue: Int?")
                        withBlock("hostHeader.split(':', limit = 2).let {", "}") {
                            write("hostOnly = it[0]")
                            write("portValue = it.getOrNull(1)?.toIntOrNull()")
                        }
                        withBlock("#T {", "}", RuntimeTypes.Http.Request.url) {
                            write("scheme = if (isHttps) #T.HTTPS else #T.HTTP", RuntimeTypes.Core.Net.Scheme, RuntimeTypes.Core.Net.Scheme)
                            write("host = #T.parse(hostOnly)", RuntimeTypes.Core.Net.Host)
                            write("if (portValue != null) port = portValue")
                            withBlock("path {", "}") {
                                write("decoded = call.request.#T()", RuntimeTypes.KtorServerRouting.requestPath)
                            }
                            withBlock("parameters {", "}") {
                                withBlock("decodedParameters {", "}") {
                                    write("call.request.queryParameters.forEach { key, values -> values.forEach { v -> add(key, v) } }")
                                }
                            }
                        }

                        write("")

                        withBlock("for (name in call.request.headers.names()) {", "}") {
                            write("val lowerName = name.lowercase()")
                            withBlock("if (lowerName != #T.Authorization.lowercase() && lowerName in signedHeaders) {", "}", RuntimeTypes.KtorServerHttp.HttpHeaders) {
                                write("call.request.headers.getAll(name)?.forEach { value -> headers.append(name, value) }")
                            }
                        }

                        write("body = #T.fromBytes(payload)", RuntimeTypes.Http.HttpBody)
                    }

                    write("")
                    withBlock("val signer = #T(", ")", RuntimeTypes.Auth.HttpAuthAws.AwsHttpSigner) {
                        withBlock("#T.Config().apply {", "}", RuntimeTypes.Auth.HttpAuthAws.AwsHttpSigner) {
                            write("this.signer = #T", RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner)
                            write("this.service = service")
                            write("this.isUnsignedPayload = isUnsigned")
                        }
                    }
                    withBlock("val attrs = #T {", "}", RuntimeTypes.Core.Collections.attributesOf) {
                        write("#T to creds", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials)
                        write("#T.SigningRegion to region", RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes)
                        write("#T.SigningDate to signingInstant", RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes)
                        withBlock("if (isUnsigned) {", "}") {
                            write(
                                "#T.HashSpecification to #T.UnsignedPayload",
                                RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
                                RuntimeTypes.Auth.Signing.AwsSigningCommon.HashSpecification,
                            )
                            write(
                                "#T.SignedBodyHeader to #T.NONE",
                                RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSigningAttributes,
                                RuntimeTypes.Auth.Signing.AwsSigningCommon.AwsSignedBodyHeader,
                            )
                        }
                    }

                    write(
                        "signer.sign(#T(requestBuilder, creds, attrs))",
                        RuntimeTypes.Auth.HttpAuthAws.SignHttpRequest,
                    )
                    write(
                        "val expectedAuth = requestBuilder.headers.getAll(#T.Authorization)?.firstOrNull() ?: return null",
                        RuntimeTypes.KtorServerHttp.HttpHeaders,
                    )
                    write("val expectedSig = expectedAuth.substringAfter(#S).trim()", "Signature=")
                    write("")
                    write("return if (expectedSig == signatureHex) creds else null")
                }
        }

        delegator.useFileWriter("SigV4A.kt", "$pkgName.auth") { writer ->
            writer.withInlineBlock("internal fun #T.sigV4A(", ")", RuntimeTypes.KtorServerAuth.AuthenticationConfig) {
                write("name: String = #S,", "aws-sigv4a")
                write("configure: SigV4AAuthProvider.Configuration.() -> Unit = {}")
            }
                .withBlock("{", "}") {
                    write("val provider = SigV4AAuthProvider(SigV4AAuthProvider.Configuration(name).apply(configure))")
                    write("register(provider)")
                }
                .write("")

            writer.withBlock("internal class SigV4AAuthProvider(config: Configuration) : #T(config) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationProvider) {
                withBlock("internal class Configuration(name: String?) : #T.Config(name) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationProvider) {
                    write("var region: String = #S", "us-east-1")
                    write("var service: String = #S", "execute-api")
                    write("var clockSkew: #T = 5.#T", KotlinTypes.Time.Duration, KotlinTypes.Time.minutes)
                }
                write("")
                write("private val region = (config as Configuration).region")
                write("private val service = config.service")
                write("private val skew = config.clockSkew")
                write("")
                withBlock("override suspend fun onAuthenticate(context: #T) {", "}", RuntimeTypes.KtorServerAuth.AuthenticationContext) {
                    write("val creds = verifySigV4A(context.call, region, service, skew)")
                    withInlineBlock("if (creds == null) {", "}") {
                        withBlock("context.challenge(#S, #T.InvalidCredentials) { challenge, call ->", "}", "AWS4-HMAC-SHA256", RuntimeTypes.KtorServerAuth.AuthenticationFailedCause) {
                            write("call.#T(#T.Unauthorized, #S)", RuntimeTypes.KtorServerRouting.responseResponse, RuntimeTypes.KtorServerHttp.HttpStatusCode, "Unauthorized")
                            write("challenge.complete()")
                        }
                    }
                    withBlock(" else {", "}") {
                        write("context.principal(UserPrincipal(creds.accessKeyId))")
                    }
                }
            }
                .write("")

            writer.withInlineBlock("public suspend fun verifySigV4A(", ")") {
                write("call: #T,", RuntimeTypes.KtorServerCore.ApplicationCallClass)
                write("region: String,")
                write("service: String,")
                write("maxClockSkew: #T", KotlinTypes.Time.Duration)
            }
                .withBlock(": #T? {", "}", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials) {
                    write("val authHeader = call.request.#T(#T.Authorization) ?: return null", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
                    write("if (!authHeader.startsWith(#S, ignoreCase = true)) return null", "AWS4-ECDSA-P256-SHA256")
                    write("")
                    write("fun part(name: String) = authHeader.substringAfter(#S).substringBefore(#S).trim()", "\$name=", ",")
                    write("")
                    write("val credential = part(#S) // accessKeyId/scope", "Credential")
                    write("val signedHeadersStr = part(#S)", "SignedHeaders")
                    write("val signatureHex = part(#S)", "Signature")
                    write("")
                    write("val signedHeaders: Set<String> = signedHeadersStr.split(';').map { it.trim().lowercase() }.toSet()")
                    write("if (#S !in signedHeaders) return null", "host")
                    write("if (!signedHeaders.any { it == #S || it == #S }) return null", "x-amz-date", "date")
                    write("val accessKeyId = credential.substringBefore(#S).takeIf { it.matches(Regex(#S)) } ?: return null", "/", "^[A-Z0-9]{16,128}$")
                    write("")
                    write("val scope = credential.substringAfter(#S, missingDelimiterValue = #S)", "/", "")
                    write("val parts = scope.split(#S)", "/")
                    write("if (parts.size != 3) return null")
                    write("val (yyyyMMdd, scopeService, term) = parts")
                    write("if (scopeService != service || term != #S) return null", "aws4_request")
                    write("if (!Regex(#S).matches(yyyyMMdd)) return null", "^\\d{8}$")
                    write("")
                    write("val regionSetHeaderName = #S", "x-amz-region-set")
                    write("val rawRegionSet = call.request.headers[regionSetHeaderName] ?: return null")
                    write("if (regionSetHeaderName !in signedHeaders) return null")
                    write("")
                    write("val regionSet: List<String> = rawRegionSet.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.ifEmpty { return null }")
                    write("")
                    withBlock("fun matchesRegion(pattern: String, value: String): Boolean {", "}") {
                        write("if (pattern == #S) return true", "*")
                        write("val rx = Regex(#S + Regex.escape(pattern).replace(#S, #S) + #S, RegexOption.IGNORE_CASE)\n", "^", "\\*", ".*", "$")
                        write("return rx.matches(value)")
                    }
                    write("if (regionSet.none { matchesRegion(it, region.lowercase()) }) return null")
                    write("")
                    write("val rawXAmzDate = call.request.#T(#S)", RuntimeTypes.KtorServerRouting.requestHeader, "X-Amz-Date")
                    write("val rawHttpDate = call.request.#T(#T.Date)", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
                    withBlock("val signingInstant: #T = when {", "}", RuntimeTypes.Core.Instant) {
                        write("rawXAmzDate != null -> { try { #T.fromIso8601(rawXAmzDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
                        write("rawHttpDate != null -> { try { #T.fromRfc5322(rawHttpDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
                        write("else -> return null")
                    }
                    write("val scopeDate = signingInstant.format(#T.ISO_8601_CONDENSED_DATE)", RuntimeTypes.Core.TimestampFormat)
                    write("if (scopeDate != yyyyMMdd) return null")
                    write("")
                    write("val now = #T.now()", RuntimeTypes.Core.Instant)
                    write("if (signingInstant < now - maxClockSkew || signingInstant > now + maxClockSkew) return null")
                    write("")
                    write("val creds = SigV4CredentialStore.get(accessKeyId) ?: return null")
                    write("")
                    write("val secTokenHeaderName = #S", "x-amz-security-token")
                    write("val secToken = call.request.headers[secTokenHeaderName]")
                    withBlock("if (creds.sessionToken != null) {", "}") {
                        write("if (secToken == null || secToken != creds.sessionToken) return null")
                        write("if (secTokenHeaderName !in signedHeaders) return null")
                    }
                    write("")
                    write("val contentSha256 = call.request.headers[#S]", "x-amz-content-sha256")
                    write("val isUnsigned = contentSha256 == #S", "UNSIGNED-PAYLOAD")
                    write("")
                    write("val origin = call.request.local")
                    write("val payload: ByteArray = call.#T<ByteArray>()", RuntimeTypes.KtorServerRouting.requestReceive)
                    write("")
                    write("val protoHeader = call.request.headers[#S] ?: origin.scheme", "X-Forwarded-Proto")
                    write("val isHttps = (protoHeader.equals(#S, ignoreCase = true))", "https")
                    write("val hostHeader = call.request.headers[#S] ?: call.request.headers[#S] ?: return null", "X-Forwarded-Host", "Host")
                    write("val hostOnly: String")
                    write("val portValue: Int?")
                    withBlock("hostHeader.split(':', limit = 2).let {", "}") {
                        write("hostOnly = it[0]")
                        write("portValue = it.getOrNull(1)?.toIntOrNull()")
                    }
                    write("")
                    write("val canonicalUri = encodeCanonicalPath(call.request.#T())", RuntimeTypes.KtorServerRouting.requestPath)
                    write("val canonicalQuery = buildCanonicalQuery(#T.build { call.request.queryParameters.forEach { k, vs -> vs.forEach { v -> append(k, v) } } })", RuntimeTypes.KtorServerHttp.Parameters)
                    write("")
                    withBlock("val filteredHeaders = #T().apply {", "}.build()", RuntimeTypes.KtorServerHttp.HeadersBuilder) {
                        withBlock("for (name in call.request.headers.names()) {", "}") {
                            write("val ln = name.lowercase()")
                            withBlock("if (ln != HttpHeaders.Authorization.lowercase() && ln in signedHeaders) {", "}") {
                                write("call.request.headers.getAll(name)?.forEach { v -> append(name, v) }")
                            }
                        }
                        withBlock("if (!names().any { it.equals(#S, ignoreCase = true) }) {", "}", "X-Amz-Region-Set") {
                            write("append(#S, rawRegionSet)", "X-Amz-Region-Set")
                        }
                        withBlock("if (!names().any { it.equals(#S, ignoreCase = true) }) {", "}", "Host") {
                            write("val defaultPort = (isHttps && portValue == 443) || (!isHttps && portValue == 80)")
                            write("append(#S, if (portValue != null && !defaultPort) #S else hostOnly) ", "Host", "\$hostOnly:\$portValue")
                        }
                    }

                    withBlock("val (canonicalHeaders, signedHeaderList) = run {", "}") {
                        write("val map = mutableMapOf<String, MutableList<String>>()")
                        withBlock("filteredHeaders.names().forEach { name ->", "}") {
                            write("val ln = name.lowercase()")
                            withBlock("if (ln in signedHeaders) {", "}") {
                                write("val values = filteredHeaders.getAll(name).orEmpty()")
                                write("    .map { it.trim().replace(Regex(#S), #S) }", "\\s+", " ")
                                write("map.getOrPut(ln) { mutableListOf() }.addAll(values)")
                            }
                        }
                        withBlock("if (#S !in map) {", "}", "x-amz-region-set") {
                            write("map[#S] = mutableListOf(rawRegionSet)", "x-amz-region-set")
                        }
                        withBlock("val canon = map.toSortedMap().entries.joinToString(#S, postfix = #S) { entry ->", "}", "\n", "\n") {
                            write("val key = entry.key")
                            write("val vs = entry.value")
                            write("\"\$key:\${vs.joinToString(#S)}\"", ",")
                        }

                        write("val signedList = map.keys.sorted().joinToString(#S)", ";")
                        write("canon to signedList")
                    }

                    write("val payloadHash = if (isUnsigned) #S else { sha256Hex(payload) }", "UNSIGNED-PAYLOAD")

                    withBlock("val canonicalRequest = buildString {", "}") {
                        write("append(call.request.#T.value.uppercase()).append('\\n')", RuntimeTypes.KtorServerRouting.requestHttpMethod)
                        write("append(canonicalUri).append('\\n')")
                        write("append(canonicalQuery).append('\\n')")
                        write("append(canonicalHeaders)")
                        write("append('\\n')") // empty line before SignedHeaders list
                        write("append(signedHeaderList).append('\\n')")
                        write("append(payloadHash)")
                    }

                    write("val crHashHex = sha256Hex(canonicalRequest.toByteArray())")

                    withBlock("val stringToSign = buildString {", "}") {
                        write("append(#S).append('\\n')", "AWS4-ECDSA-P256-SHA256")
                        write("append(signingInstant.format(#T.ISO_8601_CONDENSED)).append('\\n')", RuntimeTypes.Core.TimestampFormat)
                        write("append(#S).append('\\n')", "\$yyyyMMdd/\$service/aws4_request")
                        write("append(crHashHex)")
                    }

                    write("val publicKey: java.security.PublicKey = SigV4aPublicKeyStore.get(accessKeyId) ?: return null")
                    write("val sigDer = signatureHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()")
                    write("val verifier = java.security.Signature.getInstance(#S)", "SHA256withECDSA")
                    write("verifier.initVerify(publicKey)")
                    write("verifier.update(stringToSign.toByteArray())")
                    write("val ok = verifier.verify(sigDer)")
                    write("return if (ok) creds else null")
                }
                .write("")

            writer.withBlock("private fun sha256Hex(bytes: ByteArray): String {", "}") {
                write("return java.security.MessageDigest.getInstance(#S).digest(bytes).joinToString(#S) { #S.format(it) }", "SHA-256", "", "%02x")
            }
                .write("")

            writer.withBlock("private val UNRESERVED: BooleanArray = BooleanArray(128).apply {", "}") {
                write("for (c in 'A'..'Z') this[c.code] = true")
                write("for (c in 'a'..'z') this[c.code] = true")
                write("for (c in '0'..'9') this[c.code] = true")
                write("this['-'.code] = true; this['_'.code] = true; this['.'.code] = true; this['~'.code] = true\n")
            }
                .write("")

            writer.withBlock("private fun rfc3986Encode(bytes: ByteArray): String {", "}") {
                write("val out = StringBuilder(bytes.size * 3)")
                withBlock("for (b in bytes) {", "}") {
                    write("val i = b.toInt() and 0xFF")
                    withInlineBlock("if (i < 128 && UNRESERVED[i]) {", "}") {
                        write("out.append(i.toChar())")
                    }
                    withBlock(" else {", "}") {
                        write("out.append('%')")
                        write("val hi = #S[(i ushr 4) and 0xF]", "0123456789ABCDEF")
                        write("val lo = #S[i and 0xF]", "0123456789ABCDEF")
                        write("out.append(hi).append(lo)")
                    }
                }
                write("return out.toString()")
            }
                .write("")
            writer.write("private fun encodeString(s: String): String = rfc3986Encode(s.toByteArray(Charsets.UTF_8))")
                .write("")

            writer.withBlock("private fun encodeCanonicalPath(rawPath: String): String {", "}") {
                write("val p = if (rawPath.isEmpty()) #S else rawPath", "/")
                write("return p.split('/').joinToString(#S) { seg -> if (seg.isEmpty()) #S else encodeString(seg) }", "/", "")
            }
                .write("")

            writer.withBlock("private fun buildCanonicalQuery(params: #T): String {", "}", RuntimeTypes.KtorServerHttp.Parameters) {
                withBlock("val pairs = buildList {", "}") {
                    withBlock("params.names().sorted().forEach { name ->", "}") {
                        write("val values = params.getAll(name)")
                        openBlock("if (values == null || values.isEmpty()) {")
                        write("add(#S)", "\${encodeString(name)}=")
                        closeAndOpenBlock("} else {")
                        write("values.sorted().forEach { v -> add(#S) }", "\${encodeString(name)}=\${encodeString(v)}")
                        closeBlock("}")
                    }
                }
                write("return pairs.joinToString(#S)", "&")
            }
        }

        delegator.useFileWriter("Authentication.kt", "$pkgName.auth") { writer ->
            writer.withBlock("internal fun #T.configureAuthentication() {", "}", RuntimeTypes.KtorServerCore.Application) {
                write("")
                withBlock(
                    "#T(#T) {",
                    "}",
                    RuntimeTypes.KtorServerCore.install,
                    RuntimeTypes.KtorServerAuth.Authentication,
                ) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerAuth.bearer, "auth-bearer") {
                        write("realm = #S", "Access to API")
                        write("authenticate { cred -> BearerValidation.bearerValidation(cred.token) }")
                    }
                    withBlock("sigV4(name = #S) {", "}", "aws-sigv4") {
                        write("region = #T.region", ServiceTypes(pkgName).serviceFrameworkConfig)
                        val serviceSigV4AuthTrait = serviceShape.getTrait<SigV4Trait>()
                        if (serviceSigV4AuthTrait != null) {
                            write("service = #S", serviceSigV4AuthTrait.name)
                        }
                    }
                    withBlock("sigV4A(name = #S) {", "}", "aws-sigv4a") {
                        write("region = #T.region", ServiceTypes(pkgName).serviceFrameworkConfig)
                        val serviceSigV4AAuthTrait = serviceShape.getTrait<SigV4ATrait>()
                        if (serviceSigV4AAuthTrait != null) {
                            write("service = #S", serviceSigV4AAuthTrait.name)
                        }
                    }
                    write("provider(#S) { authenticate { ctx -> ctx.principal(Unit) } }", "no-auth")
                }
            }
        }
    }

    // For every operation request structure, create a `validate()` function file.
    override fun renderConstraintValidators() {
        ConstraintUtilsGenerator(ctx, delegator).render()
        operations.forEach { operation -> ConstraintGenerator(ctx, operation, delegator).render() }
    }

    // Writes `Routing.kt` that maps Smithy operations → Ktor routes.
    override fun renderRouting() {
        delegator.useFileWriter("Routing.kt", pkgName) { writer ->

            operations.forEach { shape ->
                writer.addImport("$pkgName.serde", "${shape.id.name}OperationDeserializer")
                writer.addImport("$pkgName.serde", "${shape.id.name}OperationSerializer")
                writer.addImport("$pkgName.constraints", "check${shape.id.name}RequestConstraint")
                writer.addImport("$pkgName.model", "${shape.id.name}Request")
                writer.addImport("$pkgName.model", "${shape.id.name}Response")
                writer.addImport("$pkgName.operations", "handle${shape.id.name}Request")
                shape.errors.forEach { error ->
                    writer.addImport("$pkgName.serde", "${error.name}Serializer")
                }
            }

            writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
                withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                    withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                        write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseResponseText, "hello world")
                    }
                    operations.filter { it.hasTrait(HttpTrait.ID) }
                        .forEach { shape ->
                            val httpTrait = shape.getTrait<HttpTrait>()!!

                            val uri = httpTrait.uri
                            val successCode = httpTrait.code
                            val method = when (httpTrait.method) {
                                "GET" -> RuntimeTypes.KtorServerRouting.get
                                "POST" -> RuntimeTypes.KtorServerRouting.post
                                "PUT" -> RuntimeTypes.KtorServerRouting.put
                                "PATCH" -> RuntimeTypes.KtorServerRouting.patch
                                "DELETE" -> RuntimeTypes.KtorServerRouting.delete
                                "HEAD" -> RuntimeTypes.KtorServerRouting.head
                                "OPTIONS" -> RuntimeTypes.KtorServerRouting.options
                                else -> error("Unsupported http trait ${httpTrait.method}")
                            }
                            val contentType = MediaType.fromServiceShape(ctx, serviceShape, shape.input.get())
                            val contentTypeGuard = when (contentType) {
                                MediaType.CBOR -> "cbor()"
                                JSON -> "json()"
                                PLAIN_TEXT -> "text()"
                                OCTET_STREAM -> "binary()"
                                ANY -> "any()"
                            }

                            val acceptType = MediaType.fromServiceShape(ctx, serviceShape, shape.output.get())
                            val acceptTypeGuard = when (acceptType) {
                                MediaType.CBOR -> "cbor()"
                                JSON -> "json()"
                                PLAIN_TEXT -> "text()"
                                OCTET_STREAM -> "binary()"
                                ANY -> "any()"
                            }

                            withBlock("#T (#S) {", "}", RuntimeTypes.KtorServerRouting.route, uri) {
                                write("#T(#T) { $contentTypeGuard }", RuntimeTypes.KtorServerCore.install, ServiceTypes(pkgName).contentTypeGuard)
                                write("#T(#T) { $acceptTypeGuard }", RuntimeTypes.KtorServerCore.install, ServiceTypes(pkgName).acceptTypeGuard)
                                withBlock(
                                    "#W",
                                    "}",
                                    { w: KotlinWriter -> renderRoutingAuth(w, shape) },
                                ) {
                                    withBlock("#T {", "}", method) {
                                        withInlineBlock("try {", "}") {
                                            write(
                                                "val request = #T.#T<ByteArray>()",
                                                RuntimeTypes.KtorServerCore.applicationCall,
                                                RuntimeTypes.KtorServerRouting.requestReceive,
                                            )
                                            write("val deserializer = ${shape.id.name}OperationDeserializer()")
                                            withBlock(
                                                "var requestObj = try { deserializer.deserialize(#T(), call, request) } catch (ex: Exception) {",
                                                "}",
                                                RuntimeTypes.Core.ExecutionContext,
                                            ) {
                                                write(
                                                    "throw #T(ex?.message ?: #S, ex)",
                                                    RuntimeTypes.KtorServerCore.BadRequestException,
                                                    "Malformed CBOR input",
                                                )
                                            }
                                            if (ctx.model.expectShape(shape.input.get()).allMembers.isNotEmpty()) {
                                                withBlock("requestObj = requestObj.copy {", "}") {
                                                    call { readHttpLabel(shape, writer) }
                                                    call { readHttpQuery(shape, writer) }
                                                }
                                            }

                                            write(
                                                "try { check${shape.id.name}RequestConstraint(requestObj) } catch (ex: Exception) { throw #T(ex?.message ?: #S, ex) }",
                                                RuntimeTypes.KtorServerCore.BadRequestException,
                                                "Error while validating constraints",
                                            )
                                            write("val responseObj = handle${shape.id.name}Request(requestObj)")
                                            write("val serializer = ${shape.id.name}OperationSerializer()")
                                            withBlock(
                                                "val response = try { serializer.serialize(#T(), responseObj) } catch (ex: Exception) {",
                                                "}",
                                                RuntimeTypes.Core.ExecutionContext,
                                            ) {
                                                write(
                                                    "throw #T(ex?.message ?: #S, ex)",
                                                    RuntimeTypes.KtorServerCore.BadRequestException,
                                                    "Malformed CBOR output",
                                                )
                                            }
                                            call { readResponseHttpHeader("responseObj", shape.output.get(), writer) }
                                            call { readResponseHttpPrefixHeader("responseObj", shape.output.get(), writer) }
                                            call { renderResponseCall("response", writer, acceptType, successCode.toString(), shape.output.get()) }
                                        }
                                        withBlock(" catch (t: Throwable) {", "}") {
                                            writeInline("val errorObj: Any? = ")
                                            withBlock("when (t) {", "}") {
                                                shape.errors.forEach { errorShapeId ->
                                                    val errorShape = ctx.model.expectShape(errorShapeId)
                                                    val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
                                                    write("is #T -> t as #T", errorSymbol, errorSymbol)
                                                }
                                                write("else -> null")
                                            }
                                            write("")
                                            write("")
                                            writeInline("val errorResponse: Pair<Any, Int>? = ")
                                            withBlock("when (errorObj) {", "}") {
                                                shape.errors.forEach { errorShapeId ->
                                                    val errorShape = ctx.model.expectShape(errorShapeId)
                                                    val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
                                                    write("is #T -> Pair(${errorShapeId.name}Serializer().serialize(#T(), errorObj), ${errorShape.getTrait<HttpErrorTrait>()?.code})", errorSymbol, RuntimeTypes.Core.ExecutionContext)
                                                }
                                                write("else -> null")
                                            }
                                            write("if (errorResponse == null) throw t")

                                            write("call.attributes.put(#T, true)", ServiceTypes(pkgName).responseHandledKey)
                                            withBlock("when (errorObj) {", "}") {
                                                shape.errors.forEach { errorShapeId ->
                                                    val errorShape = ctx.model.expectShape(errorShapeId)
                                                    val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
                                                    withBlock("is #T -> {", "}", errorSymbol) {
                                                        readResponseHttpHeader("errorObj", errorShapeId, writer)
                                                        readResponseHttpPrefixHeader("errorObj", errorShapeId, writer)
                                                    }
                                                }
                                                write("else -> null")
                                            }
                                            call { renderResponseCall("errorResponse.first", writer, acceptType, "\"\${errorResponse.second}\"", shape.output.get()) }
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private fun readHttpLabel(shape: OperationShape, writer: KotlinWriter) {
        val inputShape = ctx.model.expectShape(shape.input.get())
        inputShape.allMembers
            .filter { member -> member.value.hasTrait(HttpLabelTrait.ID) }
            .forEach { member ->
                val memberName = member.key
                val memberShape = member.value

                val httpLabelVariableName = "call.parameters[\"$memberName\"]?"
                val targetShape = ctx.model.expectShape(memberShape.target)
                writer.writeInline("$memberName = ")
                    .call {
                        renderCastingPrimitiveFromShapeType(
                            httpLabelVariableName,
                            targetShape.type,
                            writer,
                            memberShape.getTrait<TimestampFormatTrait>() ?: inputShape.getTrait<TimestampFormatTrait>(),
                            "Unsupported type ${memberShape.type} for httpLabel",
                        )
                    }
            }
    }

    private fun readHttpQuery(shape: OperationShape, writer: KotlinWriter) {
        val inputShape = ctx.model.expectShape(shape.input.get())
        val httpQueryKeys = mutableSetOf<String>()
        inputShape.allMembers
            .filter { member -> member.value.hasTrait(HttpQueryTrait.ID) }
            .forEach { member ->
                val memberName = member.key
                val memberShape = member.value
                val httpQueryTrait = memberShape.getTrait<HttpQueryTrait>()!!
                val httpQueryVariableName = "call.request.queryParameters[\"${httpQueryTrait.value}\"]?"
                val targetShape = ctx.model.expectShape(memberShape.target)
                httpQueryKeys.add(httpQueryTrait.value)
                writer.writeInline("$memberName = ")
                    .call {
                        when {
                            targetShape.isListShape -> {
                                val listMemberShape = targetShape.allMembers.values.first()
                                val listMemberTargetShapeId = ctx.model.expectShape(listMemberShape.target)
                                val httpQueryListVariableName = "(call.request.queryParameters.getAll(\"${httpQueryTrait.value}\") " +
                                    "?: call.request.queryParameters.getAll(\"${httpQueryTrait.value}[]\") " +
                                    "?: emptyList())"
                                writer.withBlock("$httpQueryListVariableName.mapNotNull{", "}") {
                                    renderCastingPrimitiveFromShapeType(
                                        "it?",
                                        listMemberTargetShapeId.type,
                                        writer,
                                        listMemberShape.getTrait<TimestampFormatTrait>() ?: targetShape.getTrait<TimestampFormatTrait>(),
                                        "Unsupported type ${memberShape.type} for list in httpLabel",
                                    )
                                }
                            }
                            else -> renderCastingPrimitiveFromShapeType(
                                httpQueryVariableName,
                                targetShape.type,
                                writer,
                                memberShape.getTrait<TimestampFormatTrait>() ?: inputShape.getTrait<TimestampFormatTrait>(),
                                "Unsupported type ${memberShape.type} for httpQuery",
                            )
                        }
                    }
            }
        val httpQueryParamsMember = inputShape.allMembers.values.firstOrNull { it.hasTrait(HttpQueryParamsTrait.ID) }
        httpQueryParamsMember?.apply {
            val httpQueryParamsMemberName = httpQueryParamsMember.memberName
            val httpQueryParamsMapShape = ctx.model.expectShape(httpQueryParamsMember.target) as MapShape
            val httpQueryParamsMapValueTypeShape = ctx.model.expectShape(httpQueryParamsMapShape.value.target)
            println(httpQueryParamsMapShape)
            val httpQueryKeysLiteral = httpQueryKeys.joinToString(", ") { "\"$it\"" }
            writer.withInlineBlock("$httpQueryParamsMemberName = call.request.queryParameters.entries().filter { (key, _) ->", "}") {
                write("key !in setOf($httpQueryKeysLiteral)")
            }
                .withBlock(".associate { (key, values) ->", "}") {
                    if (httpQueryParamsMapValueTypeShape.isListShape) {
                        write("key to values!!")
                    } else {
                        write("key to values.first()")
                    }
                }
                .withBlock(".mapValues { (_, value) ->", "}") {
                    renderCastingPrimitiveFromShapeType(
                        "value",
                        httpQueryParamsMapValueTypeShape.type,
                        writer,
                        httpQueryParamsMapValueTypeShape.getTrait<TimestampFormatTrait>() ?: httpQueryParamsMapShape.getTrait<TimestampFormatTrait>(),
                        "Unsupported type ${httpQueryParamsMapValueTypeShape.type} for httpQuery",
                    )
                }
        }
    }

    private fun renderRoutingAuth(w: KotlinWriter, shape: OperationShape) {
        val serviceAuthTrait = serviceShape.getTrait<AuthTrait>()
        val hasServiceHttpBearerAuthTrait = serviceShape.hasTrait(HttpBearerAuthTrait.ID)
        val hasServiceSigV4AuthTrait = serviceShape.hasTrait(SigV4Trait.ID)
        val hasServiceSigV4AAuthTrait = serviceShape.hasTrait(SigV4ATrait.ID)
        val authTrait = shape.getTrait<AuthTrait>()
        val hasOperationBearerAuthTrait = authTrait?.valueSet?.contains(HttpBearerAuthTrait.ID) ?: true
        val hasOperationSigV4AuthTrait = authTrait?.valueSet?.contains(SigV4Trait.ID) ?: true
        val hasOperationSigV4AAuthTrait = authTrait?.valueSet?.contains(SigV4ATrait.ID) ?: true

        val availableAuthTraitOrderedSet = authTrait?.valueSet ?: serviceAuthTrait?.valueSet ?: setOf<ShapeId>(HttpBearerAuthTrait.ID, SigV4Trait.ID, SigV4ATrait.ID)

        val authList = mutableListOf<String>()
        availableAuthTraitOrderedSet.forEach { authTraitId ->
            when (authTraitId) {
                HttpBearerAuthTrait.ID -> if (hasServiceHttpBearerAuthTrait && hasOperationBearerAuthTrait) authList.add("auth-bearer")
                SigV4Trait.ID -> if (hasServiceSigV4AuthTrait && hasOperationSigV4AuthTrait) authList.add("aws-sigv4")
                SigV4ATrait.ID -> if (hasServiceSigV4AAuthTrait && hasOperationSigV4AAuthTrait) authList.add("aws-sigv4a")
            }
        }
        authList.ifEmpty { authList.add("no-auth") }

        w.write(
            "#T(#L, strategy = #T.FirstSuccessful) {",
            RuntimeTypes.KtorServerAuth.authenticate,
            authList.joinToString(", ") { "\"$it\"" },
            RuntimeTypes.KtorServerAuth.AuthenticationStrategy,
        )
    }

    private fun readResponseHttpHeader(dataName: String, shapeId: ShapeId, writer: KotlinWriter) {
        val shape = ctx.model.expectShape(shapeId)
        shape.allMembers
            .filter { member -> member.value.hasTrait(HttpHeaderTrait.ID) }
            .forEach { member ->
                val headerName = member.value.getTrait<HttpHeaderTrait>()!!.value
                val memberName = member.key
                writer.write("call.response.headers.append(#S, $dataName.$memberName.toString())", headerName)
            }
    }

    private fun readResponseHttpPrefixHeader(dataName: String, shapeId: ShapeId, writer: KotlinWriter) {
        val shape = ctx.model.expectShape(shapeId)
        shape.allMembers
            .filter { member -> member.value.hasTrait(HttpPrefixHeadersTrait.ID) }
            .forEach { member ->
                val prefixHeaderName = member.value.getTrait<HttpPrefixHeadersTrait>()!!.value
                val memberName = member.key
                writer.withBlock("for ((suffixHeader, headerValue) in $dataName?.$memberName ?: mapOf()) {", "}") {
                    writer.write("call.response.headers.append(#S, headerValue.toString())", "$prefixHeaderName\${suffixHeader}")
                }
            }
    }

    private fun renderResponseCall(
        responseName: String,
        w: KotlinWriter,
        acceptType: MediaType,
        successCode: String,
        outputShapeId: ShapeId,
    ) {
        when (acceptType) {
            MediaType.CBOR -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseRespondBytes,
            ) {
                write("bytes = $responseName as ByteArray,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.Cbor)
                write(
                    "status = #T.fromValue($successCode.toInt()),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
            OCTET_STREAM -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseRespondBytes,
            ) {
                write("bytes = $responseName as ByteArray,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.OctetStream)
                write(
                    "status = #T.fromValue($successCode.toInt()),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
            JSON -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseResponseText,
            ) {
                write("text = $responseName as String,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.Json)
                write(
                    "status = #T.fromValue($successCode.toInt()),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
            PLAIN_TEXT -> w.withBlock(
                "#T.#T(",
                ")",
                RuntimeTypes.KtorServerCore.applicationCall,
                RuntimeTypes.KtorServerRouting.responseResponseText,
            ) {
                write("text = $responseName as String,")
                write("contentType = #T,", RuntimeTypes.KtorServerHttp.PlainText)
                write(
                    "status = #T.fromValue($successCode.toInt()),",
                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                )
            }
            ANY -> {
                val outputShape = ctx.model.expectShape(outputShapeId)
                val mediaTraits = outputShape.allMembers.values.firstNotNullOf { it.getTrait<MediaTypeTrait>() }
                w.withBlock(
                    "#T.#T(",
                    ")",
                    RuntimeTypes.KtorServerCore.applicationCall,
                    RuntimeTypes.KtorServerRouting.responseRespondBytes,
                ) {
                    write("bytes = $responseName as ByteArray,")
                    write("contentType = #S,", mediaTraits.value)
                    write(
                        "status = #T.fromValue($successCode.toInt()),",
                        RuntimeTypes.KtorServerHttp.HttpStatusCode,
                    )
                }
            }
        }
    }

    override fun renderPlugins() {
        renderErrorHandler()
        renderContentTypeGuard()
        renderAcceptTypeGuard()
    }

    private fun renderErrorHandler() {
        delegator.useFileWriter("ErrorHandler.kt", "$pkgName.plugins") { writer ->
            writer.write("internal val ResponseHandledKey = #T<Boolean>(#S)", RuntimeTypes.KtorServerUtils.AttributeKey, "ResponseHandled")
                .write("")
            writer.write("@#T", RuntimeTypes.KotlinxCborSerde.Serializable)
                .write("private data class ErrorPayload(val code: Int, val message: String)")
                .write("")
                .withInlineBlock("internal class ErrorEnvelope(", ")") {
                    write("val code: Int,")
                    write("val msg: String,")
                    write("cause: Throwable? = null,")
                }
                .withBlock(" : RuntimeException(msg, cause) {", "}") {
                    withBlock("fun toJson(json: #T = #T): String {", "}", RuntimeTypes.KotlinxJsonSerde.Json, RuntimeTypes.KotlinxJsonSerde.Json) {
                        withInlineBlock("return json.encodeToString(", ")") {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                    withBlock("fun toCbor(cbor: #T = #T { }): ByteArray {", "}", RuntimeTypes.KotlinxCborSerde.Cbor, RuntimeTypes.KotlinxCborSerde.Cbor) {
                        withInlineBlock("return cbor.#T(", ")", RuntimeTypes.KotlinxCborSerde.encodeToByteArray) {
                            write("ErrorPayload(code, message ?: #S)", "Unknown error")
                        }
                    }
                }
                .write("")
                .withInlineBlock("private suspend fun #T.respondEnvelope(", ")", RuntimeTypes.KtorServerCore.ApplicationCallClass) {
                    write("envelope: ErrorEnvelope,")
                    write("status: #T,", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                }
                .withBlock("{", "}") {
                    write("val acceptsCbor = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestAcceptItems, "application/cbor")
                    write("val acceptsJson = request.#T().any { it.value == #S }", RuntimeTypes.KtorServerRouting.requestAcceptItems, "application/json")
                    write("")
                    write("val log = #T.getLogger(#S)", RuntimeTypes.KtorLoggingSlf4j.LoggerFactory, pkgName)
                    write("log.info(#S)", "Route Error Message: \${envelope.msg}")
                    write("")
                    withBlock("when {", "}") {
                        withBlock("acceptsCbor -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseRespondBytes) {
                                write("bytes = envelope.toCbor(),")
                                write("status = status,")
                                write("contentType = #T", RuntimeTypes.KtorServerHttp.Cbor)
                            }
                        }
                        withBlock("acceptsJson -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseResponseText) {
                                write("envelope.toJson(),")
                                write("status = status,")
                                write("contentType = #T", RuntimeTypes.KtorServerHttp.Json)
                            }
                        }
                        withBlock("else -> {", "}") {
                            withBlock("#T(", ")", RuntimeTypes.KtorServerRouting.responseResponseText) {
                                write("envelope.msg,")
                                write("status = status")
                            }
                        }
                    }
                }
                .write("")
                .withBlock("internal fun #T.configureErrorHandling() {", "}", RuntimeTypes.KtorServerCore.Application) {
                    write("")
                    withBlock(
                        "#T(#T) {",
                        "}",
                        RuntimeTypes.KtorServerCore.install,
                        RuntimeTypes.KtorServerStatusPage.StatusPages,
                    ) {
                        withBlock("status(#T.Unauthorized) { call, status ->", "}", RuntimeTypes.KtorServerHttp.HttpStatusCode) {
                            write("if (call.attributes.getOrNull(#T) == true) { return@status }", ServiceTypes(pkgName).responseHandledKey)
                            write("call.attributes.put(#T, true)", ServiceTypes(pkgName).responseHandledKey)
                            write("val missing = call.request.headers[#S].isNullOrBlank()", "Authorization")
                            write("val message = if (missing) #S else #S", "Missing bearer token", "Invalid or expired bearer token")
                            write("call.respondEnvelope( ErrorEnvelope(status.value, message), status )")
                        }
                        write("")
                        withBlock("status(#T.NotFound) { call, status ->", "}", RuntimeTypes.KtorServerHttp.HttpStatusCode) {
                            write("if (call.attributes.getOrNull(#T) == true) { return@status }", ServiceTypes(pkgName).responseHandledKey)
                            write("call.attributes.put(#T, true)", ServiceTypes(pkgName).responseHandledKey)
                            write("val message = #S", "Resource not found")
                            write("call.respondEnvelope( ErrorEnvelope(status.value, message), status )")
                        }
                        write("")
                        withBlock("status(#T.MethodNotAllowed) { call, status ->", "}", RuntimeTypes.KtorServerHttp.HttpStatusCode) {
                            write("if (call.attributes.getOrNull(#T) == true) { return@status }", ServiceTypes(pkgName).responseHandledKey)
                            write("call.attributes.put(#T, true)", ServiceTypes(pkgName).responseHandledKey)
                            write("val message = #S", "Method not allowed for this resource")
                            write("call.respondEnvelope( ErrorEnvelope(status.value, message), status )")
                        }
                        write("")
                        withBlock("#T<Throwable> { call, cause ->", "}", RuntimeTypes.KtorServerStatusPage.exception) {
                            withBlock("val status = when (cause) {", "}") {
                                write(
                                    "is ErrorEnvelope -> #T.fromValue(cause.code)",
                                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                )
                                write(
                                    "is #T -> #T.BadRequest",
                                    RuntimeTypes.KtorServerCore.BadRequestException,
                                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                )
                                write(
                                    "is #T -> #T.PayloadTooLarge",
                                    RuntimeTypes.KtorServerBodyLimit.PayloadTooLargeException,
                                    RuntimeTypes.KtorServerHttp.HttpStatusCode,
                                )
                                write("else -> #T.InternalServerError", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                            }
                            write("")

                            write("val envelope = if (cause is ErrorEnvelope) cause else ErrorEnvelope(status.value, cause.message ?: #S)", "Unexpected error")
                            write("call.attributes.put(#T, true)", ServiceTypes(pkgName).responseHandledKey)
                            write("call.respondEnvelope( envelope, status )")
                        }
                    }
                }
        }
    }

    private fun renderContentTypeGuard() {
        delegator.useFileWriter("ContentTypeGuard.kt", "$pkgName.plugins") { writer ->

            writer.withBlock("private fun #T.hasBody(): Boolean {", "}", RuntimeTypes.KtorServerRouting.requestApplicationRequest) {
                write(
                    "return (#T()?.let { it > 0 } == true) || headers.contains(#T.TransferEncoding)",
                    RuntimeTypes.KtorServerRouting.requestContentLength,
                    RuntimeTypes.KtorServerHttp.HttpHeaders,
                )
            }
            writer.withBlock("public class ContentTypeGuardConfig {", "}") {
                write("public var allow: List<#T> = emptyList()", RuntimeTypes.KtorServerHttp.ContentType)
                write("")
                withBlock("public fun any(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Any)
                }
                write("")
                withBlock("public fun json(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Json)
                }
                write("")
                withBlock("public fun cbor(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Cbor)
                }
                write("")
                withBlock("public fun text(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.PlainText)
                }
                write("")
                withBlock("public fun binary(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.OctetStream)
                }
            }
                .write("")

            writer.withInlineBlock(
                "public val ContentTypeGuard: #T<ContentTypeGuardConfig> = #T(",
                ")",
                RuntimeTypes.KtorServerCore.ApplicationRouteScopedPlugin,
                RuntimeTypes.KtorServerCore.ApplicationCreateRouteScopedPlugin,
            ) {
                write("name = #S,", "ContentTypeGuard")
                write("createConfiguration = ::ContentTypeGuardConfig,")
            }
                .withBlock("{", "}") {
                    write("val allowed: List<#T> = pluginConfig.allow", RuntimeTypes.KtorServerHttp.ContentType)
                    write("require(allowed.isNotEmpty()) { #S }", "ContentTypeGuard installed with empty allow-list.")
                    write("")
                    withBlock("onCall { call ->", "}") {
                        write("if (!call.request.hasBody()) return@onCall")
                        write("val incoming = call.request.#T()", RuntimeTypes.KtorServerRouting.requestContentType)
                        withBlock("if (incoming == #T.Any || allowed.none { incoming.match(it) }) {", "}", RuntimeTypes.KtorServerHttp.ContentType) {
                            withBlock("throw #T(", ")", ServiceTypes(pkgName).errorEnvelope) {
                                write("#T.UnsupportedMediaType.value, ", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                write("#S", "Not acceptable Content‑Type found: '\${incoming}'. Accepted content types: \${allowed.joinToString()}")
                            }
                        }
                    }
                }
        }
    }

    private fun renderAcceptTypeGuard() {
        delegator.useFileWriter("AcceptTypeGuard.kt", "${ctx.settings.pkg.name}.plugins") { writer ->

            writer.withBlock(
                "private fun #T.acceptedContentTypes(): List<#T> {",
                "}",
                RuntimeTypes.KtorServerRouting.requestApplicationRequest,
                RuntimeTypes.KtorServerHttp.ContentType,
            ) {
                write("val raw = headers[#T.Accept] ?: return emptyList()", RuntimeTypes.KtorServerHttp.HttpHeaders)
                write(
                    "return #T(raw).mapNotNull { it.value?.let(#T::parse) }",
                    RuntimeTypes.KtorServerHttp.parseAndSortHeader,
                    RuntimeTypes.KtorServerHttp.ContentType,
                )
            }

            writer.withBlock("public class AcceptTypeGuardConfig {", "}") {
                write("public var allow: List<#T> = emptyList()", RuntimeTypes.KtorServerHttp.ContentType)
                write("")
                withBlock("public fun any(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Any)
                }
                write("")
                withBlock("public fun json(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Json)
                }
                write("")
                withBlock("public fun cbor(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.Cbor)
                }
                write("")
                withBlock("public fun text(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.PlainText)
                }
                write("")
                withBlock("public fun binary(): Unit {", "}") {
                    write("allow = listOf(#T)", RuntimeTypes.KtorServerHttp.OctetStream)
                }
            }
                .write("")

            writer.withInlineBlock(
                "public val AcceptTypeGuard: #T<AcceptTypeGuardConfig> = #T(",
                ")",
                RuntimeTypes.KtorServerCore.ApplicationRouteScopedPlugin,
                RuntimeTypes.KtorServerCore.ApplicationCreateRouteScopedPlugin,
            ) {
                write("name = #S,", "AcceptTypeGuard")
                write("createConfiguration = ::AcceptTypeGuardConfig,")
            }
                .withBlock("{", "}") {
                    write("val allowed: List<#T> = pluginConfig.allow", RuntimeTypes.KtorServerHttp.ContentType)
                    write("require(allowed.isNotEmpty()) { #S }", "AcceptTypeGuard installed with empty allow-list.")
                    write("")
                    withBlock("onCall { call ->", "}") {
                        write("val accepted = call.request.acceptedContentTypes()")
                        write("if (accepted.isEmpty()) return@onCall")
                        write("")
                        write("val isOk = accepted.any { candidate -> allowed.any { candidate.match(it) } }")

                        withBlock("if (!isOk) {", "}") {
                            withBlock("throw #T(", ")", ServiceTypes(pkgName).errorEnvelope) {
                                write("#T.NotAcceptable.value, ", RuntimeTypes.KtorServerHttp.HttpStatusCode)
                                write("#S", "Not acceptable Accept type found: '\${accepted}'. Accepted types: \${allowed.joinToString()}")
                            }
                        }
                    }
                }
        }
    }

    // Emits one stub handler per Smithy operation (`OperationNameHandler.kt`).
    override fun renderPerOperationHandlers() {
        operations.forEach { shape ->
            val name = shape.id.name
            delegator.useFileWriter("${name}Operation.kt", "$pkgName.operations") { writer ->
                writer.addImport("$pkgName.model", "${shape.id.name}Request")
                writer.addImport("$pkgName.model", "${shape.id.name}Response")

                writer.withBlock("public fun handle${name}Request(req: ${name}Request): ${name}Response {", "}") {
                    write("// TODO: implement me")
                    write("// To build a ${name}Response object:")
                    write("//   1. Use`${name}Response.Builder()`")
                    write("//   2. Set fields like `${name}Response.variable = ...`")
                    write("//   3. Return the built object using `return ${name}Response.build()`")
                    write("return ${name}Response.Builder().build()")
                }
            }
        }
    }
}
