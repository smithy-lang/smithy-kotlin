/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.AuthSchemeOption
import aws.smithy.kotlin.runtime.http.auth.*
import aws.smithy.kotlin.runtime.http.interceptors.InterceptorExecutor
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.identity.asIdentityProviderConfig
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.attributesOf
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpAuthHandlerTest {
    private val testAttrKey = AttributeKey<String>("HttpAuthHandlerTest")

    @Test
    fun testAuthOptionSigningPropertiesPropagation() = runTest {
        // verify resolved auth scheme option attributes make it to the signer
        val inner = object : Handler<SdkHttpRequest, Unit> {
            override suspend fun call(request: SdkHttpRequest) = Unit
        }
        val ctx = ExecutionContext()
        val interceptorExec = InterceptorExecutor<Unit, Unit>(ctx, emptyList(), OperationTypeInfo(Unit::class, Unit::class))
        // seed internal state required
        interceptorExec.readBeforeExecution(Unit)

        val idpConfig = AnonymousIdentityProvider.asIdentityProviderConfig()
        val scheme = object : HttpAuthScheme {
            override val schemeId: AuthSchemeId = AuthSchemeId.Anonymous
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
            listOf(AuthSchemeOption(AuthSchemeId.Anonymous, attrs))
        }

        val authConfig = OperationAuthConfig(resolver, listOf(scheme), idpConfig)
        val op = HttpAuthHandler<Unit, Unit>(inner, interceptorExec, authConfig)
        val request = SdkHttpRequest(ctx, HttpRequestBuilder())
        op.call(request)

        // ensure signer was called
        assertTrue(request.subject.headers.contains("x-test", "signed"))
    }
}
