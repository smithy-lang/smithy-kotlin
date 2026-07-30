/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.auth.*
import aws.smithy.kotlin.runtime.http.interceptors.InterceptorExecutor
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.util.RecordingTelemetryProvider
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig
import aws.smithy.kotlin.runtime.identity.asIdentityProviderConfig
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpAuthHandlerTest {
    private val testAttrKey = AttributeKey<String>("HttpAuthHandlerTest")

    @Test
    fun testAuthOptionPropertiesPropagation() = runTest {
        // verify resolved auth scheme option attributes make it to the signer and identity provider
        val inner = object : Handler<SdkHttpRequest, Unit> {
            override suspend fun call(request: SdkHttpRequest) = Unit
        }
        val ctx = ExecutionContext()
        val interceptorExec = InterceptorExecutor<Unit, Unit>(ctx, emptyList(), OperationTypeInfo(Unit::class, Unit::class))
        // seed internal state required
        interceptorExec.readBeforeExecution(Unit)

        val idpConfig = AnonymousIdentityProvider.asIdentityProviderConfig()
        val scheme = object : AuthScheme {
            override val schemeId: AuthSchemeId = AuthSchemeId.Anonymous
            override fun identityProvider(identityProviderConfig: IdentityProviderConfig): IdentityProvider = object : IdentityProvider {
                override suspend fun resolve(attributes: Attributes): Identity {
                    assertEquals("testing", attributes[testAttrKey])
                    return AnonymousIdentity
                }
            }

            override val signer: HttpSigner = object : HttpSigner {
                override suspend fun sign(signingRequest: SignHttpRequest) {
                    assertEquals("testing", signingRequest.signingAttributes[testAttrKey])
                    signingRequest.httpRequest.headers.append("x-test", "signed")
                }
            }
        }

        val resolver = AuthSchemeResolver {
            val attrs = attributesOf {
                testAttrKey to "testing"
            }
            listOf(AuthOption(AuthSchemeId.Anonymous, attrs))
        }

        val schemes = listOf(scheme).associateBy(AuthScheme::schemeId)
        val authConfig = OperationAuthConfig(resolver, schemes, idpConfig)
        val op = AuthHandler<Unit, Unit>(inner, interceptorExec, authConfig)
        val request = SdkHttpRequest(ctx, HttpRequestBuilder())
        op.call(request)

        // ensure signer was called
        assertTrue(request.subject.headers.contains("x-test", "signed"))
    }

    @Test
    fun testEndpointResolverInvoked() = runTest {
        // verify resolved auth scheme option attributes make it to the signer and identity provider
        val inner = object : Handler<SdkHttpRequest, Unit> {
            override suspend fun call(request: SdkHttpRequest) = Unit
        }
        val ctx = ExecutionContext()
        val interceptorExec = InterceptorExecutor<Unit, Unit>(ctx, emptyList(), OperationTypeInfo(Unit::class, Unit::class))
        // seed internal state required
        interceptorExec.readBeforeExecution(Unit)

        val endpointResolver = EndpointResolver {
            Endpoint("https://localhost")
        }

        val op = AuthHandler<Unit, Unit>(inner, interceptorExec, OperationAuthConfig.Anonymous, endpointResolver)
        val request = SdkHttpRequest(ctx, HttpRequestBuilder())
        op.call(request)

        assertEquals(Scheme.HTTPS, request.subject.url.scheme)
        assertEquals(Host.Domain("localhost"), request.subject.url.host)
    }

    @OptIn(ExperimentalApi::class)
    @Test
    fun testAuthMetricsCarryRpcAttributesAndContext() = runTest {
        // verify identity-resolution and signing metrics are tagged with rpc.service/rpc.method and
        // carry the current telemetry context
        val inner = object : Handler<SdkHttpRequest, Unit> {
            override suspend fun call(request: SdkHttpRequest) = Unit
        }

        val provider = RecordingTelemetryProvider()
        val ctx = ExecutionContext()
        ctx[SdkClientOption.ServiceName] = "TestService"
        ctx[SdkClientOption.OperationName] = "TestOperation"
        ctx[HttpOperationContext.OperationMetrics] = OperationMetrics("TestService", provider)

        val interceptorExec = InterceptorExecutor<Unit, Unit>(ctx, emptyList(), OperationTypeInfo(Unit::class, Unit::class))
        // seed internal state required
        interceptorExec.readBeforeExecution(Unit)

        val op = AuthHandler<Unit, Unit>(inner, interceptorExec, OperationAuthConfig.Anonymous)
        val request = SdkHttpRequest(ctx, HttpRequestBuilder())
        op.call(request)

        listOf(
            "smithy.client.call.auth.resolve_identity_duration",
            "smithy.client.call.auth.signing_duration",
        ).forEach { name ->
            assertMetricHasRpcAttributesAndContext(provider, name)
        }
    }

    @OptIn(ExperimentalApi::class)
    @Test
    fun testEndpointResolutionMetricCarriesRpcAttributesAndContext() = runTest {
        // verify the endpoint-resolution metric is tagged with rpc.service/rpc.method and carries the
        // current telemetry context (previously it used operationAttributes and omitted the context)
        val inner = object : Handler<SdkHttpRequest, Unit> {
            override suspend fun call(request: SdkHttpRequest) = Unit
        }

        val provider = RecordingTelemetryProvider()
        val ctx = ExecutionContext()
        ctx[SdkClientOption.ServiceName] = "TestService"
        ctx[SdkClientOption.OperationName] = "TestOperation"
        ctx[HttpOperationContext.OperationMetrics] = OperationMetrics("TestService", provider)

        val interceptorExec = InterceptorExecutor<Unit, Unit>(ctx, emptyList(), OperationTypeInfo(Unit::class, Unit::class))
        // seed internal state required
        interceptorExec.readBeforeExecution(Unit)

        val endpointResolver = EndpointResolver {
            Endpoint("https://localhost")
        }

        val op = AuthHandler<Unit, Unit>(inner, interceptorExec, OperationAuthConfig.Anonymous, endpointResolver)
        val request = SdkHttpRequest(ctx, HttpRequestBuilder())
        op.call(request)

        assertMetricHasRpcAttributesAndContext(provider, "smithy.client.call.resolve_endpoint_duration")
    }

    /**
     * Asserts that exactly one measurement was recorded for [name] and that it carries the rpc.service/rpc.method
     * attributes and the provider's current telemetry context.
     */
    @OptIn(ExperimentalApi::class)
    private fun assertMetricHasRpcAttributesAndContext(provider: RecordingTelemetryProvider, name: String) {
        val rpcServiceKey = AttributeKey<String>("rpc.service")
        val rpcMethodKey = AttributeKey<String>("rpc.method")

        val records = provider.recordsFor(name)
        assertEquals(1, records.size, "expected exactly one $name measurement")
        val record = records.single()
        assertEquals("TestService", record.attributes.getOrNull(rpcServiceKey), "$name missing rpc.service attribute")
        assertEquals("TestOperation", record.attributes.getOrNull(rpcMethodKey), "$name missing rpc.method attribute")
        assertNotNull(record.context, "$name missing telemetry context")
        assertSame(provider.activeContext, record.context, "$name did not carry the current telemetry context")
    }
}
