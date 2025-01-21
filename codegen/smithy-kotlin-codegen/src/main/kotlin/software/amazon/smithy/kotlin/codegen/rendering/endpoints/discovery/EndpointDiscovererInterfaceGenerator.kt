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
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscovererInterfaceGenerator(private val ctx: CodegenContext, private val delegator: KotlinDelegator) {
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
            dokka("Represents the logic for automatically discovering endpoints for ${ctx.settings.sdkId} calls")
            withBlock(
                "#L interface #T {",
                "}",
                ctx.settings.api.visibility,
                symbol,
            ) {
                write(
                    "#1L fun asEndpointResolver(client: #2T, delegate: #3T): #3T",
                    ctx.settings.api.visibility,
                    clientSymbol,
                    RuntimeTypes.HttpClient.Operation.EndpointResolver,
                )
                write("")
                renderDiscoverHost()
                write("")
                write("public suspend fun invalidate(context: #T)", RuntimeTypes.Core.ExecutionContext)
            }
            write("")
            write(
                "#L data class DiscoveryParams(private val region: String?, private val identity: String)",
                ctx.settings.api.visibility,
            )
            write(
                """#1L val DiscoveryParamsKey: #2T<DiscoveryParams> = #2T("DiscoveryParams")""",
                ctx.settings.api.visibility,
                RuntimeTypes.Core.Collections.AttributeKey,
            )
        }
    }

    private fun KotlinWriter.renderDiscoverHost() {
        openBlock(
            "#L suspend fun discoverHost(client: #T): #T<#T> =",
            ctx.settings.api.visibility,
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
}
