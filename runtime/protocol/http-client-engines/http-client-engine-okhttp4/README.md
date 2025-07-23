# OkHttp4 Engine

The AWS SDK for Kotlin depends on a stable version of OkHttp **5.x**.

This `OkHttp4Engine` is intended to be used for applications which still depend on okhttp3 **4.x** and can't upgrade to the next major version.

## Configuration

### Gradle
Because the SDK's default HTTP engine depends on okhttp3 **5.x**, consumers will need to force Gradle to resolve to **4.x** to prevent the newer dependency from being introduced transitively. Here is a sample configuration:
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
    exclude(group = "com.squareup.okhttp3", module = "okhttp-coroutines") // Exclude dependency on okhttp-coroutines, which was introduced in 5.0.0-alpha.X 
}
```

### AWS SDK for Kotlin
You will also need to configure your SDK's HTTP client to use the `OkHttp4Engine`:
```kt
import aws.sdk.kotlin.services.s3.*
import aws.smithy.kotlin.runtime.http.engine.okhttp4.OkHttp4Engine
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    OkHttp4Engine().use { okHttp4Engine ->
        S3Client.fromEnvironment {
            httpClient = okHttp4Engine
        }.use {
            // use the client!
        }
    }
}
```

For more tips on configuring the HTTP client, [see our developer guide entry](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/http-client-config.html).

## Troubleshooting

### java.lang.NoClassDefFoundError
If you see an exception similar to this...
```
java.lang.NoClassDefFoundError: okhttp3/coroutines/ExecuteAsyncKt
	at aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine.roundTrip(OkHttpEngine.kt:56) ~[http-client-engine-okhttp-jvm-1.3.9-SNAPSHOT.jar:?]
	at aws.smithy.kotlin.runtime.http.engine.internal.ManagedHttpClientEngine.roundTrip(ManagedHttpClientEngine.kt) ~[http-client-jvm-1.3.9-SNAPSHOT.jar:?]
	at aws.smithy.kotlin.runtime.http.SdkHttpClient$executeWithCallContext$2.invokeSuspend(SdkHttpClient.kt:44) ~[http-client-jvm-1.3.9-SNAPSHOT.jar:?]
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33) ~[kotlin-stdlib-2.0.10.jar:2.0.10-release-540]
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104) ~[kotlinx-coroutines-core-jvm-1.8.1.jar:?]
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:584) ~[kotlinx-coroutines-core-jvm-1.8.1.jar:?]
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:811) ~[kotlinx-coroutines-core-jvm-1.8.1.jar:?]
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:715) ~[kotlinx-coroutines-core-jvm-1.8.1.jar:?]
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:702) ~[kotlinx-coroutines-core-jvm-1.8.1.jar:?]
Caused by: java.lang.ClassNotFoundException: okhttp3.coroutines.ExecuteAsyncKt
	at jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641) ~[?:?]
	at jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188) ~[?:?]
	at java.lang.ClassLoader.loadClass(ClassLoader.java:525) ~[?:?]
	... 9 more
Exception in thread "main" java.lang.NoClassDefFoundError: okhttp3/coroutines/ExecuteAsyncKt
	at aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine.roundTrip(OkHttpEngine.kt:56)
	at aws.smithy.kotlin.runtime.http.engine.internal.ManagedHttpClientEngine.roundTrip(ManagedHttpClientEngine.kt)
	at aws.smithy.kotlin.runtime.http.SdkHttpClient$executeWithCallContext$2.invokeSuspend(SdkHttpClient.kt:44)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:584)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:811)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:715)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:702)
Caused by: java.lang.ClassNotFoundException: okhttp3.coroutines.ExecuteAsyncKt
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525)
	... 9 more
```

It likely means you failed to configure the SDK client to use the `OkHttpEngine4`. 
Please double-check all of your SDK client configurations to ensure `httpClient = OkHttpEngine4()` is configured,
and if the problem persists, [open an issue](https://github.com/smithy-lang/smithy-kotlin/issues/new/choose).

### Android R8 / ProGuard Configuration
If you're using the OkHttp4Engine in an Android application with R8 or Proguard for code minification, and you see an error similar to the following:
```
ERROR: R8: Missing class okhttp3.coroutines.ExecuteAsyncKt (referenced from: java.lang.Object aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine.roundTrip(aws.smithy.kotlin.runtime.operation.ExecutionContext, aws.smithy.kotlin.runtime.http.request.HttpRequest, kotlin.coroutines.Continuation))
```

You'll need to add the following rule to either `proguard-rules.pro` or `consumer-rules.pro`, depending on your project structure:
```
-dontwarn okhttp3.coroutines.ExecuteAsyncKt
```