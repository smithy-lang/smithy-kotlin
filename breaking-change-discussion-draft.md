An upcoming release of the **AWS SDK for Kotlin** will upgrade our dependency on OkHttp to **v5.0.0-alpha.14**. This version contains a number of breaking changes and earlier versions of the SDK are not compatible with it.

# Release date

This feature will ship with the **v1.2.0** release planned for **4/22/2024**.

# What's changing

The Kotlin SDK will be upgrading its dependency on OkHttp to v5.0.0-alpha.14. If you don't directly depend on OkHttp alongside the AWS SDK for Kotlin 
*AND* you don't depend on different versions of the AWS SDK for Kotlin in the same project, this change should not affect you.

If you _do_ have direct dependencies on OkHttp and the AWS SDK for Kotlin, you will need to upgrade to OkHttp **v5.0.0-alpha.14** along with your upgrade to AWS SDK for Kotlin **v1.2.0**.

If you _do_ depend on different version of the AWS SDK for Kotlin in the same project, you will need to make sure they are all upgraded to **v1.2.0** together. Versions of the SDK prior to **v1.2.0** will no longer be compatible with newer versions.

If you've _already_ upgraded your OkHttp version and are experiencing this error, it will be resolved by upgrading to AWS SDK for Kotlin **v1.2.0**:  `java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/JvmCallExtensionsKt;`

# How to migrate

1. Upgrade all of your AWS SDK for Kotlin dependencies to **v.1.2.0**.
2. Upgrade your OkHttp dependency to **v5.0.0-alpha.14**.
3. Resolve any issues caused by OkHttp's breaking changes. See [their change log](https://square.github.io/okhttp/changelogs/changelog/) for information.

# Feedback

If you have any questions concerning this change, please feel free to engage with us in this discussion. If you encounter a bug with these changes, please [file an issue](https://github.com/awslabs/aws-sdk-kotlin/issues/new/choose).