/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.auth.*
import aws.smithy.kotlin.runtime.http.interceptors.InterceptorExecutor
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig
import aws.smithy.kotlin.runtime.identity.asIdentityProviderConfig
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.attributesOf
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
}
