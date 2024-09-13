# OkHttp4 Engine

The AWS SDK for Kotlin depends on OkHttp **5.0.0-alpha.x**, which despite being in alpha, is claimed to be production stable and safe for consumption:

> Although this release is labeled alpha, the only unstable thing in it is our new APIs. 
> This release has many critical bug fixes and is safe to run in production. 
> We’re eager to stabilize our new APIs so we can get out of alpha.
> 
> https://square.github.io/okhttp/changelogs/changelog/#version-500-alpha12

This `OkHttp4Engine` is intended to be used for applications which still depend on okhttp3 **4.x** and can't upgrade to the newest alpha version.

## Configuration

### Gradle
Because the SDK's default HTTP engine depends on okhttp3 **5.0.0-alpha.X**, consumers will need to force Gradle to resolve to **4.x** to prevent the alpha dependency from being introduced transitively. Here is a sample configuration:
```kts
dependencies {
    implementation("aws.sdk.kotlin:s3:$SDK_VERSION") // and any other AWS SDK clients... 
    implementation("aws.smithy.kotlin:http-client-engine-okhttp4:$SMITHY_KOTLIN_VERSION") // depend on OkHttp4Engine
}

configurations.all {
    resolutionStrategy {
        // Force resolve to OkHttp 4.x
        force("com.squareup.okhttp3:okhttp:4.12.0") // or whichever version you are using... 
    }
    exclude(group = "com.squareup.okhttp3", module = "okhttp-coroutines") // Exclude dependency on okhttp-coroutines, which is introduced in 5.0.0-alpha.X 
}
```

### AWS SDK for Kotlin
You will also need to configure your SDK's HTTP client to use the `OkHttp4Engine`:
```kt
import aws.sdk.kotlin.services.s3.*
import aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    S3Client.fromEnvironment {
        httpClient = OkHttp4Engine()
    }.use {
        // use the client!
    }
}
```

For more tips on configuring the HTTP client, [see our developer guide entry](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/http-client-config.html).