/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.awsprotocol.eventstream

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.hashing.sha256
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventStreamSigningTest {
    private val testCredentials = Credentials("fake access key", "fake secret key")
    private val testCredentialsProvider = object : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes) = testCredentials
    }

    @Test
    fun testSignPayload() = runTest {
        val messageToSign = buildMessage {
            addHeader("some-header", HeaderValue.String("value"))
            payload = "test payload".encodeToByteArray()
        }

        val epoch = Instant.fromEpochSeconds(123_456_789L, 1234)
        val testClock = ManualClock(epoch)
        val signingConfig = AwsSigningConfig.Builder().apply {
            credentials = testCredentials
            region = "us-east-1"
            service = "testservice"
            signatureType = AwsSignatureType.HTTP_REQUEST_EVENT
        }

        val prevSignature = "last message sts".encodeToByteArray().sha256().encodeToHex().encodeToByteArray()

        val buffer = SdkBuffer()
        messageToSign.encode(buffer)
        val messagePayload = buffer.readByteArray()
        val result = DefaultAwsSigner.signPayload(signingConfig, prevSignature, messagePayload, testClock)
        assertEquals(":date", result.output.headers[0].name)

        val dateHeader = result.output.headers[0].value.expectTimestamp()
        assertEquals(epoch.epochSeconds, dateHeader.epochSeconds)
        assertEquals(0, dateHeader.nanosecondsOfSecond)

        assertEquals(":chunk-signature", result.output.headers[1].name)
        // signature is hex encoded string bytes, the header value is the raw bytes
        val expectedSignature = result.signature.decodeToString()
        val actualSignature = result.output.headers[1].value.expectByteArray().encodeToHex()
        assertEquals(expectedSignature, actualSignature)

        val expected = "1ea04a4f6becd85ae3e38e379ffaf4bb95042603f209512476cc6416868b31ee"
        assertEquals(expected, actualSignature)
    }

    @Test
    fun testEmptyEndFrameSent() = runTest {
        val messageToSign = buildMessage {
            addHeader("some-header", HeaderValue.String("value"))
            payload = "test payload".encodeToByteArray()
        }

        val context = ExecutionContext()
        context[AwsSigningAttributes.Signer] = DefaultAwsSigner
        context[AwsSigningAttributes.RequestSignature] = CompletableDeferred(HashSpecification.EmptyBody.hash.encodeToByteArray())
        context[AwsSigningAttributes.SigningRegion] = "us-east-2"
        context[AwsSigningAttributes.SigningService] = "test"
        context[AwsSigningAttributes.CredentialsProvider] = testCredentialsProvider

        val signedEvents = flowOf(messageToSign).sign(context).toList()
        // 1 message + empty signed frame
        assertEquals(2, signedEvents.size)
    }
}
