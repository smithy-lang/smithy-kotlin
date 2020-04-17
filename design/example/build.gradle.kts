// FIXME - We probably need some kind of option for generating a top level project or not. Or this is something we can customize in the AWS specific code generator
//         The actual (AWS) SDK's will just use the kotlin plugin and not set a version, define repositories, etc
// FIXME - might need settings for targeting JVM vs android (or configuring both)

plugins {
    kotlin("jvm") version "1.3.71"
}

group = "com.xyzcorp"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":client-runtime"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
