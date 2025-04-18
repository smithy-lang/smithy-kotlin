/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "kotlinRepoTools"
            url = java.net.URI("https://d2gys1nrxnjnyg.cloudfront.net/releases")
            content {
                includeGroupByRegex("""aws\.sdk\.kotlin.*""")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

// TODO This is largely shared with aws-sdk-kotlin, consider commonizing
// Set up a sibling directory aws-crt-kotlin as a composite build, if it exists.
// Allows overrides via local.properties:
// compositeProjects=~/repos/aws-crt-kotlin,/tmp/some/other/thing,../../another/project
val compositeProjectList = try {
    val localProperties = java.util.Properties().also {
        it.load(File(rootProject.projectDir, "local.properties").inputStream())
    }
    val compositeProjects = localProperties.getProperty("compositeProjects") ?: "../aws-crt-kotlin"

    val compositeProjectPaths = compositeProjects.split(",")
        .map { it.replaceFirst("^~".toRegex(), System.getProperty("user.home")) } // expand ~ to user's home directory
        .filter { it.isNotBlank() }
        .map { file(it) }

    compositeProjectPaths.also {
        if (it.isNotEmpty()) {
            println("Adding composite build projects from local.properties: ${compositeProjectPaths.joinToString { it.name }}")
        }
    }
} catch (_: Throwable) {
    logger.error("Could not load composite project paths from local.properties")
    listOf(file("../aws-crt-kotlin"))
}

compositeProjectList.forEach {
    if (it.exists()) {
        println("Including build '$it'")
        includeBuild(it)
    } else {
        println("Ignoring invalid build directory '$it'")
    }
}

rootProject.name = "smithy-kotlin"

include(":dokka-smithy")

include(":bom")
include(":runtime")
include(":runtime:auth:aws-credentials")
include(":runtime:auth:aws-signing-common")
include(":runtime:auth:aws-signing-crt")
include(":runtime:auth:aws-signing-default")
include(":runtime:auth:aws-signing-tests")
include(":runtime:auth:http-auth")
include(":runtime:auth:http-auth-api")
include(":runtime:auth:http-auth-aws")
include(":runtime:auth:identity-api")
include(":runtime:crt-util")
include(":runtime:observability:logging-slf4j2")
include(":runtime:observability:telemetry-api")
include(":runtime:observability:telemetry-defaults")
include(":runtime:observability:telemetry-provider-otel")
include(":runtime:observability:telemetry-provider-micrometer")
include(":runtime:protocol:aws-protocol-core")
include(":runtime:protocol:aws-event-stream")
include(":runtime:protocol:aws-json-protocols")
include(":runtime:protocol:aws-xml-protocols")
include(":runtime:protocol:smithy-rpcv2-protocols")
include(":runtime:protocol:http")
include(":runtime:protocol:http-client")
include(":runtime:protocol:http-client-engines:http-client-engine-crt")
include(":runtime:protocol:http-client-engines:http-client-engine-default")
include(":runtime:protocol:http-client-engines:http-client-engine-okhttp")
include(":runtime:protocol:http-client-engines:http-client-engine-okhttp4")
include(":runtime:protocol:http-client-engines:test-suite")
include(":runtime:protocol:http-test")
include(":runtime:runtime-core")
include(":runtime:serde")
include(":runtime:serde:serde-form-url")
include(":runtime:serde:serde-json")
include(":runtime:serde:serde-xml")
include(":runtime:serde:serde-cbor")
include(":runtime:smithy-client")
include(":runtime:smithy-test")
include(":runtime:testing")

include(":codegen:smithy-kotlin-codegen")
include(":codegen:smithy-kotlin-codegen-testutils")
include(":codegen:smithy-aws-kotlin-codegen")
include(":codegen:protocol-tests")

include(":tests")
include(":tests:benchmarks:aws-signing-benchmarks")
include(":tests:benchmarks:channel-benchmarks")
include(":tests:benchmarks:http-benchmarks")
include(":tests:benchmarks:serde-benchmarks")
include(":tests:compile")
include(":tests:codegen:nullability-tests")
include(":tests:codegen:paginator-tests")
include(":tests:codegen:serde-tests")
include(":tests:codegen:serde-codegen-support")
include(":tests:codegen:waiter-tests")
include(":tests:integration:slf4j-1x-consumer")
include(":tests:integration:slf4j-2x-consumer")
include(":tests:integration:slf4j-hybrid-consumer")
