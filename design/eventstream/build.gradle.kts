plugins {
    kotlin("jvm") version "1.3.71"
    application
}

group = "com.amazonaws"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "com.amazonaws.smithy.poc.Main"
}


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
