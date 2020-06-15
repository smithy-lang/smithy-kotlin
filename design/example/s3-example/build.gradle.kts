plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
//    implementation(project(":client-rt"))
//    implementation("io.ktor:ktor-client-core-jvm:1.3.2")
    implementation(project(":client-runtime:client-rt-core"))
    implementation(project(":client-runtime:protocol:http"))
    implementation(project(":client-runtime:protocol:http-client-engines:http-client-engine-ktor"))

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
