/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.auth.signing

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSignedBodyHeader
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.DefaultAwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.crt.CrtAwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.tests.DEFAULT_TEST_CREDENTIALS
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

private const val DEFAULT_SIGNER = "default"
private const val CRT_SIGNER = "crt"

private val signers = mapOf(
    DEFAULT_SIGNER to DefaultAwsSigner,
    CRT_SIGNER to CrtAwsSigner,
)

private val payload = (';'..'z').joinToString("").repeat(16).encodeToByteArray() // 1KB

private val requestToSign = HttpRequest {
    method = HttpMethod.GET
    url {
        scheme = Scheme.HTTPS
        host = Host.Domain("foo.com")
        path.decoded = "bar/baz/../qux/"
        port = 8080
    }
    headers {
        append("Content-Type", "application/x-www-form-urlencoded")
        append("Cache-Control", "no-cache")
    }
    body = HttpBody.fromBytes(payload)
}

private val config = AwsSigningConfig {
    region = "the-moon"
    service = "fooservice"
    signedBodyHeader = AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
    credentials = DEFAULT_TEST_CREDENTIALS
}

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class AwsSignerBenchmark {
    @Param(DEFAULT_SIGNER, CRT_SIGNER)
    var signerName: String = ""

    @Benchmark
    fun signingBenchmark() = runBlocking {
        val signer = signers.getValue(signerName)
        signer.sign(requestToSign, config)
    }
}
