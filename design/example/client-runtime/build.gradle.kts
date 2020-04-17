plugins {
    kotlin("jvm")
}

group = "com.amazonaws.smithy.runtime"
version = "0.1.0"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.1")
}

