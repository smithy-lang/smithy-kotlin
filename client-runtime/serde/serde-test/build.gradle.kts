description = "Protocol independent and cross-protocol integration tests"
extra["displayName"] = "Smithy :: Kotlin :: Serde :: Test"
extra["moduleName"] = "software.aws.clientrt.serde.test"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":client-runtime:serde"))
                api(project(":client-runtime:serde:serde-json"))
                api(project(":client-runtime:serde:serde-xml"))
            }
        }
    }
}
