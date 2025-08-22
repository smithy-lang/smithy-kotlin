package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes

/**
 * Entry point for writing plugin modules (error handling, content-type guard, accept-type guard).
 *
 * Generates three files under the `plugins` package:
 * - `ErrorHandler.kt`: common error envelope and exception-to-response mapping
 * - `ContentTypeGuard.kt`: validates request `Content-Type` header
 * - `AcceptTypeGuard.kt`: validates request `Accept` header
 */
internal fun KtorStubGenerator.writePlugins() {
    renderErrorHandler()
    renderContentTypeGuard()
    renderAcceptTypeGuard()
}

/**
 * Generates `ErrorHandler.kt`, which contains:
 * - `ErrorEnvelope` exception wrapper for standard error responses
 * - JSON and CBOR serialization for error payloads
 * - Extension to respond with an `ErrorEnvelope`
 * - `configureErrorHandling()` that installs a `StatusPages` plugin
 *   mapping HTTP status codes and exceptions → structured error responses
 */
private fun KtorStubGenerator.renderErrorHandler() {
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
                        write("val message = if (missing) #S else #S", "Missing bearer token", "Invalid or expired authentication")
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

/**
 * Generates `ContentTypeGuard.kt`, which installs a route-scoped plugin that:
 * - Defines a configurable allow-list of acceptable request `Content-Type`s
 * - Rejects unsupported media types with an `ErrorEnvelope`
 * - Provides convenience configuration (e.g., `json()`, `cbor()`, `binary()`)
 */
private fun KtorStubGenerator.renderContentTypeGuard() {
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

/**
 * Generates `AcceptTypeGuard.kt`, which installs a route-scoped plugin that:
 * - Defines a configurable allow-list of acceptable `Accept` header values
 * - Rejects unsupported response types with an `ErrorEnvelope`
 * - Provides convenience configuration (e.g., `json()`, `cbor()`, `text()`)
 */
private fun KtorStubGenerator.renderAcceptTypeGuard() {
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
