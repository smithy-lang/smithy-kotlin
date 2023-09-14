/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientEndpointDiscoveryTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointResolverAdapterGenerator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscovererGenerator(private val ctx: CodegenContext, private val delegator: KotlinDelegator) {
    private val symbol = symbolFor(ctx.settings)
    private val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
    private val clientSymbol = ctx.symbolProvider.toSymbol(service)
    private val operationName = run {
        val epDiscoveryTrait = service.expectTrait<ClientEndpointDiscoveryTrait>()
        val operation = ctx.model.expectShape<OperationShape>(epDiscoveryTrait.operation)
        operation.defaultName()
    }

    companion object {
        fun symbolFor(settings: KotlinSettings): Symbol = buildSymbol {
            val clientName = clientName(settings.sdkId)
            name = "${clientName}EndpointDiscoverer"
            namespace = "${settings.pkg.name}.endpoints"
        }
    }

    fun render() {
        delegator.applyFileWriter(symbol) {
            dokka(
                """
                    A class which looks up specific endpoints for ${ctx.settings.sdkId} calls via the `$operationName`
                    API. These unique endpoints are cached as appropriate to avoid unnecessary latency in subsequent
                    calls.
                """.trimIndent(),
            )
            withBlock(
                "#L class #T {",
                "}",
                ctx.settings.api.visibility.value,
                symbol,
            ) {
                write(
                    "private val cache = #T<DiscoveryParams, #T>(10.#T, #T.System)",
                    RuntimeTypes.Core.Utils.ReadThroughCache,
                    RuntimeTypes.Core.Net.Host,
                    KotlinTypes.Time.minutes,
                    RuntimeTypes.Core.Clock,
                )
                write("")
                renderAsEndpointResolver()
                write("")
                renderDiscoverHost()
                write("")
                renderInvalidate()
            }
            write("")
            write(
                """private val discoveryParamsKey = #T<DiscoveryParams>("DiscoveryParams")""",
                RuntimeTypes.Core.Utils.AttributeKey,
            )
            write("private data class DiscoveryParams(private val region: String?, private val identity: String)")
        }
    }

    private fun KotlinWriter.renderAsEndpointResolver() {
        withBlock(
            "internal fun asEndpointResolver(client: #T, delegate: #T) = #T { request ->",
            "}",
            clientSymbol,
            EndpointResolverAdapterGenerator.getSymbol(ctx.settings),
            RuntimeTypes.HttpClient.Operation.EndpointResolver,
        ) {
            write("val identity = request.identity")
            write(
                """require(identity is #T) { "Endpoint discovery requires AWS credentials" }""",
                RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials,
            )
            write("")
            write("val cacheKey = DiscoveryParams(client.config.region, identity.accessKeyId)")
            write("request.context[discoveryParamsKey] = cacheKey")
            write("val discoveredHost = cache.get(cacheKey) { discoverHost(client) }")
            write("")
            write("val originalEndpoint = delegate.resolve(request)")
            withBlock("#T(", ")", RuntimeTypes.SmithyClient.Endpoints.Endpoint) {
                write("originalEndpoint.uri.copy(host = discoveredHost),")
                write("originalEndpoint.headers,")
                write("originalEndpoint.attributes,")
            }
        }
    }

    private fun KotlinWriter.renderDiscoverHost() {
        openBlock(
            "private suspend fun discoverHost(client: #T): #T<#T> =",
            clientSymbol,
            RuntimeTypes.Core.Utils.ExpiringValue,
            RuntimeTypes.Core.Net.Host,
        )
        // ASSUMPTION No services which use discovery include parameters to the EP operation (despite being
        // possible according to the Smithy spec).
        write("client.#L()", operationName)
        indent()
        write(".endpoints")
        withBlock("?.map { ep -> #T(", ")}", RuntimeTypes.Core.Utils.ExpiringValue) {
            write("#T.parse(ep.address!!),", RuntimeTypes.Core.Net.Host)
            write("#T.now() + ep.cachePeriodInMinutes.#T,", RuntimeTypes.Core.Instant, KotlinTypes.Time.minutes)
        }
        write("?.firstOrNull()")
        write(
            """?: throw #T("Unable to discover any endpoints when invoking #L!")""",
            RuntimeTypes.SmithyClient.Endpoints.EndpointProviderException,
            operationName,
        )
        dedent(2)
    }

    private fun KotlinWriter.renderInvalidate() {
        withBlock("internal suspend fun invalidate(context: #T) {", "}", RuntimeTypes.Core.ExecutionContext) {
            write("context.getOrNull(discoveryParamsKey)?.let { cache.invalidate(it) }")
        }
    }
}
