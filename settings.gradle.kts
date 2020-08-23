pluginManagement {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        google()
        gradlePluginPortal()
    }
}

rootProject.name = "smithy-kotlin"
enableFeaturePreview("GRADLE_METADATA")

include(":smithy-kotlin-codegen")
include(":smithy-kotlin-codegen-test")

include(":client-runtime")
include(":client-runtime:testing")
include(":client-runtime:smithy-test")
include(":client-runtime:client-rt-core")
include(":client-runtime:utils")
include(":client-runtime:io")
include(":client-runtime:protocol:http")
include(":client-runtime:serde")
include(":client-runtime:serde:serde-json")
include(":client-runtime:serde:serde-xml")
include(":client-runtime:serde:serde-test")

include(":client-runtime:protocol:http-client-engines:http-client-engine-ktor")

// FIXME - intellij DOES NOT like this project included. Everything builds fine but intellij 2020.x
// will not resolve symbols from dependencies in any other subproject once included. 2019.x seems to work fine
// to run with from CLI use `./gradlew -DandroidEmulatorTests=true :android-test:connectedAndroidTest`
if (System.getProperties().containsKey("androidEmulatorTests")) {
    // android integration test project to verify client-runtime support on Android devices
    // at target API levels
    include(":android-test")
}

// for now include the POC project
include(":example")
project(":example").projectDir = file("./design/example")
include(":example:mock-server")
include(":example:lambda-example")
include(":example:s3-example")

