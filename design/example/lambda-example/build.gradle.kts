plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
//    implementation(project(":client-rt"))
//    implementation("io.ktor:ktor-client-core-jvm:1.3.2")
    implementation("software.aws.smithy.kotlin:client-rt-core:0.0.1")
    implementation("software.aws.smithy.kotlin:http-jvm:0.0.1")
    implementation("software.aws.smithy.kotlin:http-client-engine-ktor-jvm:0.0.1")

    // FIXME - this is only necessary for a conversion from ByteStream to HttpBody (which belongs in client runtime)
    implementation("software.aws.smithy.kotlin:io:0.0.1")

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
