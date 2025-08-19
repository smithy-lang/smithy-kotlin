package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.rendering.serde.deserializerName
import software.amazon.smithy.kotlin.codegen.rendering.serde.serializerName
import software.amazon.smithy.kotlin.codegen.service.MediaType
import software.amazon.smithy.kotlin.codegen.service.MediaType.ANY
import software.amazon.smithy.kotlin.codegen.service.MediaType.JSON
import software.amazon.smithy.kotlin.codegen.service.MediaType.OCTET_STREAM
import software.amazon.smithy.kotlin.codegen.service.MediaType.PLAIN_TEXT
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes
import software.amazon.smithy.kotlin.codegen.service.renderCastingPrimitiveFromShapeType
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

/**
 * Generates Ktor server routing for all operations in the service model.
 *
 * - Creates `Routing.kt` file.
 * - Installs appropriate content-type and accept-type guards.
 * - Handles request deserialization, validation, business logic invocation,
 *   response serialization, and error handling.
 */
internal fun KtorStubGenerator.writeRouting() {
    delegator.useFileWriter("Routing.kt", pkgName) { writer ->
        operations.forEach { shape ->
            writer.addImport("$pkgName.constraints", "check${shape.id.name}RequestConstraint")
            writer.addImport("$pkgName.operations", "handle${shape.id.name}Request")
        }

        writer.withBlock("internal fun #T.configureRouting(): Unit {", "}", RuntimeTypes.KtorServerCore.Application) {
            withBlock("#T {", "}", RuntimeTypes.KtorServerRouting.routing) {
                withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerRouting.get, "/") {
                    write(" #T.#T(#S)", RuntimeTypes.KtorServerCore.applicationCall, RuntimeTypes.KtorServerRouting.responseResponseText, "hello world")
                }
                operations.filter { it.hasTrait(HttpTrait.ID) }
                    .forEach { shape ->
                        val inputShape = ctx.model.expectShape(shape.input.get())
                        val inputSymbol = ctx.symbolProvider.toSymbol(inputShape)

                        val outputShape = ctx.model.expectShape(shape.output.get())
                        val outputSymbol = ctx.symbolProvider.toSymbol(outputShape)

                        val serializerSymbol = buildSymbol {
                            definitionFile = "${shape.serializerName()}.kt"
                            name = shape.serializerName()
                            namespace = ctx.settings.pkg.serde
                            reference(inputSymbol, SymbolReference.ContextOption.DECLARE)
                        }
                        val deserializerSymbol = buildSymbol {
                            definitionFile = "${shape.deserializerName()}.kt"
                            name = shape.deserializerName()
                            namespace = ctx.settings.pkg.serde
                            reference(outputSymbol, SymbolReference.ContextOption.DECLARE)
                        }

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
                                        write("val deserializer = #T()", deserializerSymbol)
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
                                        write("val serializer = #T()", serializerSymbol)
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
                                        writeInline("val errorResponse: Pair<Any, Int>? = ")
                                        withBlock("when (errorObj) {", "}") {
                                            shape.errors.forEach { errorShapeId ->
                                                val errorShape = ctx.model.expectShape(errorShapeId)
                                                val errorSymbol = ctx.symbolProvider.toSymbol(errorShape)
                                                val exceptionSymbol = buildSymbol {
                                                    val exceptionName = "${errorSymbol.name}Serializer"
                                                    definitionFile = "$errorSymbol.kt"
                                                    name = exceptionName
                                                    namespace = ctx.settings.pkg.serde
                                                    reference(errorSymbol, SymbolReference.ContextOption.DECLARE)
                                                }
                                                write("is #T -> Pair(#T().serialize(#T(), errorObj), ${errorShape.getTrait<HttpErrorTrait>()?.code})", errorSymbol, exceptionSymbol, RuntimeTypes.Core.ExecutionContext)
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

/**
 * Reads `HttpLabelTrait` annotated members from request URI parameters
 * and casts them to appropriate Kotlin types before populating request object.
 */
private fun KtorStubGenerator.readHttpLabel(shape: OperationShape, writer: KotlinWriter) {
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

/**
 * Reads `HttpQueryTrait` and `HttpQueryParamsTrait` annotated members
 * from query parameters. Handles both simple and list-valued query params,
 * casting them to correct Kotlin types before populating request object.
 */
private fun KtorStubGenerator.readHttpQuery(shape: OperationShape, writer: KotlinWriter) {
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

/**
 * Configures authentication for a given operation shape.
 * Determines available authentication strategies (Bearer, SigV4, SigV4A)
 * at service and operation level and installs them in Ktor's `authenticate` block.
 */
private fun KtorStubGenerator.renderRoutingAuth(w: KotlinWriter, shape: OperationShape) {
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

/**
 * Reads and appends HTTP headers from response object fields annotated
 * with `HttpHeaderTrait` to the Ktor response.
 */
private fun KtorStubGenerator.readResponseHttpHeader(dataName: String, shapeId: ShapeId, writer: KotlinWriter) {
    val shape = ctx.model.expectShape(shapeId)
    shape.allMembers
        .filter { member -> member.value.hasTrait(HttpHeaderTrait.ID) }
        .forEach { member ->
            val headerName = member.value.getTrait<HttpHeaderTrait>()!!.value
            val memberName = member.key
            writer.write("call.response.headers.append(#S, $dataName.$memberName.toString())", headerName)
        }
}

/**
 * Reads and appends HTTP prefix headers from response object fields annotated
 * with `HttpPrefixHeadersTrait`. Dynamically appends prefixed headers with suffix values.
 */
private fun KtorStubGenerator.readResponseHttpPrefixHeader(dataName: String, shapeId: ShapeId, writer: KotlinWriter) {
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

/**
 * Writes the Ktor call to send the response back to the client.
 *
 * - Selects correct responder (`respondBytes` or `responseText`) based on content type.
 * - Sets appropriate content type and HTTP status code.
 * - Supports CBOR, JSON, text, binary, and dynamic media types.
 */
private fun KtorStubGenerator.renderResponseCall(
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
