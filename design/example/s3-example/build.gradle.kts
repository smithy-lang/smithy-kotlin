plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(project(":client-runtime:client-rt-core"))
    implementation(project(":client-runtime:protocol:http"))
    implementation(project(":client-runtime:protocol:http:features:http-serde"))
    implementation(project(":client-runtime:protocol:http-client-engines:http-client-engine-ktor"))
    implementation(project(":client-runtime:serde"))
    // S3 would obviously be XML
    implementation(project(":client-runtime:serde:serde-xml"))

    // FIXME - this is only necessary for a conversion from ByteStream to HttpBody (which belongs in client runtime)
    implementation(project(":client-runtime:io"))

    // FIXME - this isn't necessary it's only here for the example main function
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
}

//compileKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
//
//compileTestKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
