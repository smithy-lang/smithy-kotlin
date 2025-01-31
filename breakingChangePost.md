An upcoming release of the **AWS SDK for Kotlin** will introduce an optional region provider parameter that determines the AWS region when configuring a client

# Release date

This change will be included in the upcoming **v1.5.0** release,

# What's changing
Adding an optional `regionProvider` parameter to provide more flexibility in how regions are specified.
This affects the configuration of AWS service clients.

The SDK will resolve the region in the following priority order:
1. Static region (if specified using `region = "..."`)
2. Custom region provider (if specified using `regionProvider = ...`)
3. Default region provider chain

Example usage:
```kotlin
val myRegionProvider = RegionProviderChain(src1, src2, src3, ...)

val s3 = S3Client.fromEnvironment {
    regionProvider = myRegionProvider
}
```
If a static region is specified, the value of regionProvider will not be used:

```kotlin
val myRegionProvider = ...

val s3 = S3Client.fromEnvironment {
    regionProvider = myRegionProvider // Ignored since `region` is also set
    region = "moon-east-1"
}
```

# How to migrate
Update your imports to reflect the new file locations:

```kotlin
//old imports
import aws.sdk.kotlin.runtime.region.RegionProvider
import aws.sdk.kotlin.runtime.region.RegionProviderChain
```

```kotlin
//new imports
import aws.smithy.kotlin.runtime.client.region.RegionProvider
import aws.smithy.kotlin.runtime.client.region.RegionProviderChain
```

# Additional information

For more information about this change, see https://github.com/awslabs/aws-sdk-kotlin/issues/1478.

# Feedback

If you have any questions concerning this change, please feel free to engage
with us in this discussion. If you encounter a bug with these changes when
released, please [file an issue](https://github.com/awslabs/aws-sdk-kotlin/issues/new/choose).