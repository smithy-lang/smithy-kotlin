package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

/**
 * AwsChunked Encoding
 * not thread-safe! do not use multiple executors to read from the same AwsChunked object
 */
internal expect class AwsChunked internal constructor(
    chan: SdkByteReadChannel,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig,
    previousSignature: ByteArray,
): AbstractAwsChunked
