An upcoming release of the **AWS SDK for Kotlin** will move several region-related classes into different namespaces and modules as part of an internal refactoring.
This change was necessary to support adding `regionProvider` client config property, see https://github.com/awslabs/aws-sdk-kotlin/issues/1478 for more details.

# Release date

This change will be included in the upcoming **v1.5.0** release.

# What's changing

The `RegionProvider` and `RegionProviderChain` classes are now available from different modules than before. **This will affect your build if you're using these classes**.

# How to migrate

## 1. Update build dependencies 
If your code uses region-related classes, you may need to add a new dependency in your `build.gradle.kts`:

```kotlin
implementation("aws.smithy.kotlin:smithy-client:<version>")
```

## 2. Update import statements
After updating the dependencies, you'll need to modify your import statements:

Replace these imports:

```kotlin
import aws.sdk.kotlin.runtime.region.RegionProvider
import aws.sdk.kotlin.runtime.region.RegionProviderChain
```
With:

```kotlin
import aws.smithy.kotlin.runtime.client.region.RegionProvider
import aws.smithy.kotlin.runtime.client.region.RegionProviderChain
```

No changes to your existing implementation code are required beyond updating the dependencies and imports. Your code should continue to function as before once the dependencies and imports are updated.

# Additional information

For more information about this change, see https://github.com/awslabs/aws-sdk-kotlin/issues/1478.

# Feedback

If you have any questions concerning this change, please feel free to engage
with us in this discussion. If you encounter a bug with these changes when
released, please [file an issue](https://github.com/awslabs/aws-sdk-kotlin/issues/new/choose).