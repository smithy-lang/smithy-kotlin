# BREAKING: Disabling automatic response decompression

An upcoming release of the **AWS SDK for Kotlin** will disable the automatic decompression of response bodies when using the OkHttp engine.

# Release date

This feature will ship with the **v1.1.0** release planned for **3/18/2024**.

# What's changing

When using the default HTTP engine OkHttp, the underlying HTTP client will no longer automatically decompress response bodies when they are Gzip compressed. Previously OkHttp would transparently decompress response bodies when the `Content-Encoding: gzip` header was present. (See [OkHttp's documentation on calls](https://square.github.io/okhttp/features/calls/) for more detail.) No other HTTP engine automatically decompresses response bodies.

This may manifest when retrieving an object from S3 that was uploaded with a `Content-Encoding: gzip` header. For instance, if compressed and uncompressed data exist like this:

```kotlin
val uncompressed = ByteArray(1024) { it.mod(Byte.MAX_VALUE) }
println("Uncompressed length: ${uncompressed.size}") // Uncompressed length: 1024

val compressed = ByteArrayOutputStream()
    .also { GZIPOutputStream(it, true).apply {
        write(uncompressed)
        flush()
        close()
    } }
    .toByteArray()
println("Compressed length: ${compressed.size}") // Compressed length: 164 (may vary depending on JVM version)
```

And the compressed data is uploaded to S3:

```kotlin
s3.putObject {
    bucket = "<some-bucket>"
    key = "myCompressedObj"
    contentEncoding = "gzip"
    body = ByteStream.fromBytes(compressed)
}
```

Then the object may retrieved with a `getObject` call:

```kotlin
s3.getObject(GetObjectRequest {
    bucket = "<some-bucket>"
    key = "myCompressedObj"
}) { response ->
    println("Response length: ${response.body?.toByteArray()?.size}")
    println("Content-Encoding: ${response.contentEncoding}")
}
```

**Before the change** the `getObject` example prints:

```
Response length: 164
Content-Encoding: null
```

**After the change** the `getObject` example prints:

```
Response length: 1024
Content-Encoding: gzip
```

# How to migrate

If you are using the default OkHttp engine and **do not want** to decompress response bodies, the no change is necessary.

If you **do want** to decompress response bodies, you will need to update your code. Response bodies may be decompressed using JVM's [`GZIPInputStream`](https://docs.oracle.com/javase/8/docs/api/java/util/zip/GZIPInputStream.html) and the [`ByteStream.toInputStream()`](https://sdk.amazonaws.com/kotlin/api/smithy-kotlin/api/latest/runtime-core/aws.smithy.kotlin.runtime.content/to-input-stream.html) extension function:

```kotlin
val uncompressedBytes = s3.getObject(GetObjectRequest {
    bucket = "<some-bucket>"
    key = "myCompressedObj"
}) { response ->
    val body = response.body ?: error("no body received")
    when (response.contentEncoding) {
        "gzip" -> GZIPInputStream(body.toInputStream()).readBytes()
        else -> body.toByteArray()
    }
}
// ...make use of `uncompressedBytes`...
```

To apply automatic decompression to multiple response bodies you may consider writing an [interceptor](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/interceptors.html).

# Feedback

If you have any questions concerning this change, please feel free to engage with us in this discussion. If you encounter a bug with these changes, please [file an issue](https://github.com/awslabs/aws-sdk-kotlin/issues/new/choose).

