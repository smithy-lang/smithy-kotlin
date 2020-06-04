plugins {
    kotlin("jvm")
    application
}


repositories {
    mavenLocal()
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
}

val ktor_version: String = "1.3.2"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("ch.qos.logback:logback-classic:1.2.1")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")
}

application {
    mainClassName = "com.example.ApplicationKt"
}


// compileKotlin {
//    kotlinOptions.jvmTarget = "1.8"
// }
//
// compileTestKotlin {
//    kotlinOptions.jvmTarget = "1.8"
// }
