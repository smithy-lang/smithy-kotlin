package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.closeAndOpenBlock
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes

internal fun KtorStubGenerator.writeAWSAuthentication() {
    delegator.useFileWriter("AWSValidation.kt", "$pkgName.auth") { writer ->
        writer.withBlock("internal object SigV4CredentialStore {", "}") {
            write("private val table: Map<String, #T> = mapOf()", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials)
            withBlock("internal fun get(accessKeyId: String): #T? {", "}", RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials) {
                write("// TODO: implement me:")
                write("//  Look up the credentials associated with this accessKeyId.")
                write("//  Return a Credentials object like this:")
                write("//    Credentials(")
                write("//        accessKeyId = #S,", "<AWS_ACCESS_KEY_ID>")
                write("//        secretAccessKey = #S,", "<AWS_SECRET_ACCESS_KEY>")
                write("//        sessionToken = #S,", "<OPTIONAL_SESSION_TOKEN_IF_ANY>")
                write("//    )")
                write("return table[accessKeyId]")
            }
        }
        writer.write("")
        writer.withBlock("internal object SigV4aPublicKeyStore {", "}") {
            write("private val table: Map<String, java.security.PublicKey> = mapOf()")
            write("")
            withBlock("internal fun get(accessKeyId: String): java.security.PublicKey? {", "}") {
                write("// TODO: implement me:")
                write("//  Look up the public key associated with this accessKeyId.")
                write("//  Example if loading from bytes:")
                write("//    val spec = X509EncodedKeySpec(keyBytes)")
                write("//    val kf = KeyFactory.getInstance(\"EC\")")
                write("//    return kf.generatePublic(spec)")
                write("//  Return the java.security.PublicKey that should be used to verify SigV4A ECDSA signatures.")
                write("return table[accessKeyId]")
            }
        }
    }

    delegator.useFileWriter("AWSSigV4.kt", "$pkgName.auth") { writer ->
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
                retrieveAuthInformation(writer, "AWS4-HMAC-SHA256")
                write("")
                write("val scope = credential.substringAfter(#S, missingDelimiterValue = #S)", "/", "")
                write("val parts = scope.split(#S)", "/")
                write("if (parts.size != 4) return null")
                write("val (yyyyMMdd, scopeRegion, scopeService, term) = parts")
                write("if (scopeRegion != region || scopeService != service || term != #S) return null", "aws4_request")
                write("if (!Regex(#S).matches(yyyyMMdd)) return null", "^\\d{8}$")
                write("")
                authDateValidation(writer)
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
                createHttpRequestBuilder(writer)
                write("")
                validateSigV4(writer)
                write("")
                write("return if (expectedSig == signatureHex) creds else null")
            }
    }

    delegator.useFileWriter("AWSSigV4A.kt", "$pkgName.auth") { writer ->
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
                retrieveAuthInformation(writer, "AWS4-ECDSA-P256-SHA256")
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
                    write("val normalized = pattern.trim().replace(Regex(#S), #S)", "\\*+", "*")
                    write("val sb = StringBuilder(#S)", "^")
                    write("val parts = normalized.split(#S)", "*")
                    withBlock("parts.forEachIndexed { i, part ->", "}") {
                        write("sb.append(Regex.escape(part))")
                        write("if (i < parts.lastIndex) sb.append(#S)", "[^-]+")
                    }
                    write("sb.append(#S)", "$")
                    write("return Regex(sb.toString(), RegexOption.IGNORE_CASE).matches(value.trim())")
                }
                write("if (regionSet.none { matchesRegion(it, region.lowercase()) }) return null")
                write("")
                authDateValidation(writer)
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
                createCanonicalRequest(writer)
                write("")
                validateSigV4A(writer)
                write("")
                write("return if (ok) creds else null")
            }
            .write("")
        renderHelperFunctions(writer)
    }
}

private fun retrieveAuthInformation(writer: KotlinWriter, algorithm: String) {
    writer.write("val authHeader = call.request.#T(#T.Authorization) ?: return null", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
        .write("if (!authHeader.startsWith(#S, ignoreCase = true)) return null", algorithm)
        .write("")
        .write("fun part(name: String) = authHeader.substringAfter(#S).substringBefore(#S).trim()", "\$name=", ",")
        .write("")
        .write("val credential = part(#S) // accessKeyId/scope", "Credential")
        .write("val signedHeadersStr = part(#S)", "SignedHeaders")
        .write("val signatureHex = part(#S)", "Signature")
        .write("")
        .write("val signedHeaders: Set<String> = signedHeadersStr.split(';').map { it.trim().lowercase() }.toSet()")
        .write("if (#S !in signedHeaders) return null", "host")
        .write("if (!signedHeaders.any { it == #S || it == #S }) return null", "x-amz-date", "date")
        .write("val accessKeyId = credential.substringBefore(#S).takeIf { it.matches(Regex(#S)) } ?: return null", "/", "^[A-Z0-9]{16,128}$")
}

private fun authDateValidation(writer: KotlinWriter) {
    writer.write("val rawXAmzDate = call.request.#T(#S)", RuntimeTypes.KtorServerRouting.requestHeader, "X-Amz-Date")
        .write("val rawHttpDate = call.request.#T(#T.Date)", RuntimeTypes.KtorServerRouting.requestHeader, RuntimeTypes.KtorServerHttp.HttpHeaders)
        .withBlock("val signingInstant: #T = when {", "}", RuntimeTypes.Core.Instant) {
            write("rawXAmzDate != null -> { try { #T.fromIso8601(rawXAmzDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
            write("rawHttpDate != null -> { try { #T.fromRfc5322(rawHttpDate) } catch (_: Exception) { return null } }", RuntimeTypes.Core.Instant)
            write("else -> return null")
        }
        .write("val scopeDate = signingInstant.format(#T.ISO_8601_CONDENSED_DATE)", RuntimeTypes.Core.TimestampFormat)
        .write("if (scopeDate != yyyyMMdd) return null")
        .write("")
        .write("val now = #T.now()", RuntimeTypes.Core.Instant)
        .write("if (signingInstant < now - maxClockSkew || signingInstant > now + maxClockSkew) return null")
}

private fun createHttpRequestBuilder(writer: KotlinWriter) {
    writer.write("val origin = call.request.local")
        .write("val payload: ByteArray = call.#T<ByteArray>()", RuntimeTypes.KtorServerRouting.requestReceive)
        .write("")
        .withBlock("val requestBuilder: #T = #T().apply {", "}", RuntimeTypes.Http.Request.HttpRequestBuilder, RuntimeTypes.Http.Request.HttpRequestBuilder) {
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
}

private fun createCanonicalRequest(writer: KotlinWriter) {
    writer.write("val origin = call.request.local")
        .write("val payload: ByteArray = call.#T<ByteArray>()", RuntimeTypes.KtorServerRouting.requestReceive)
        .write("")
        .write("val protoHeader = call.request.headers[#S] ?: origin.scheme", "X-Forwarded-Proto")
        .write("val isHttps = (protoHeader.equals(#S, ignoreCase = true))", "https")
        .write("val hostHeader = call.request.headers[#S] ?: call.request.headers[#S] ?: return null", "X-Forwarded-Host", "Host")
        .write("val hostOnly: String")
        .write("val portValue: Int?")
        .withBlock("hostHeader.split(':', limit = 2).let {", "}") {
            write("hostOnly = it[0]")
            write("portValue = it.getOrNull(1)?.toIntOrNull()")
        }
        .write("")
        .write("val canonicalUri = encodeCanonicalPath(call.request.#T())", RuntimeTypes.KtorServerRouting.requestPath)
        .write("val canonicalQuery = buildCanonicalQuery(#T.build { call.request.queryParameters.forEach { k, vs -> vs.forEach { v -> append(k, v) } } })", RuntimeTypes.KtorServerHttp.Parameters)
        .write("")
        .withBlock("val filteredHeaders = #T().apply {", "}.build()", RuntimeTypes.KtorServerHttp.HeadersBuilder) {
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
        .withBlock("val (canonicalHeaders, signedHeaderList) = run {", "}") {
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
        .write("val payloadHash = if (isUnsigned) #S else { sha256Hex(payload) }", "UNSIGNED-PAYLOAD")
        .withBlock("val canonicalRequest = buildString {", "}") {
            write("append(call.request.#T.value.uppercase()).append('\\n')", RuntimeTypes.KtorServerRouting.requestHttpMethod)
            write("append(canonicalUri).append('\\n')")
            write("append(canonicalQuery).append('\\n')")
            write("append(canonicalHeaders)")
            write("append('\\n')") // empty line before SignedHeaders list
            write("append(signedHeaderList).append('\\n')")
            write("append(payloadHash)")
        }
}

private fun validateSigV4(writer: KotlinWriter) {
    writer.withBlock("val signer = #T(", ")", RuntimeTypes.Auth.HttpAuthAws.AwsHttpSigner) {
        withBlock("#T.Config().apply {", "}", RuntimeTypes.Auth.HttpAuthAws.AwsHttpSigner) {
            write("this.signer = #T", RuntimeTypes.Auth.Signing.AwsSigningStandard.DefaultAwsSigner)
            write("this.service = service")
            write("this.isUnsignedPayload = isUnsigned")
        }
    }
        .withBlock("val attrs = #T {", "}", RuntimeTypes.Core.Collections.attributesOf) {
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
        .write(
            "signer.sign(#T(requestBuilder, creds, attrs))",
            RuntimeTypes.Auth.HttpAuthAws.SignHttpRequest,
        )
        .write(
            "val expectedAuth = requestBuilder.headers.getAll(#T.Authorization)?.firstOrNull() ?: return null",
            RuntimeTypes.KtorServerHttp.HttpHeaders,
        )
        .write("val expectedSig = expectedAuth.substringAfter(#S).trim()", "Signature=")
}

private fun validateSigV4A(writer: KotlinWriter) {
    writer.write("val crHashHex = sha256Hex(canonicalRequest.toByteArray())")
        .withBlock("val stringToSign = buildString {", "}") {
            write("append(#S).append('\\n')", "AWS4-ECDSA-P256-SHA256")
            write("append(signingInstant.format(#T.ISO_8601_CONDENSED)).append('\\n')", RuntimeTypes.Core.TimestampFormat)
            write("append(#S).append('\\n')", "\$yyyyMMdd/\$service/aws4_request")
            write("append(crHashHex)")
        }
        .write("val publicKey: java.security.PublicKey = SigV4aPublicKeyStore.get(accessKeyId) ?: return null")
        .write("val sigDer = signatureHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()")
        .write("val verifier = java.security.Signature.getInstance(#S)", "SHA256withECDSA")
        .write("verifier.initVerify(publicKey)")
        .write("verifier.update(stringToSign.toByteArray())")
        .write("val ok = verifier.verify(sigDer)")
}

private fun renderHelperFunctions(writer: KotlinWriter) {
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
