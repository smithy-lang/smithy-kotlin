rootProject.name = "smithy-kotlin"
enableFeaturePreview("GRADLE_METADATA")

include(":smithy-kotlin-codegen")
include(":smithy-kotlin-codegen-test")

include(":client-runtime")
include(":client-runtime:aws-smithy-client-rt-core")
