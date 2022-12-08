/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

description = "OkHttp Client Engine for Smithy services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: HTTP :: Engine :: OkHttp"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.engine.okhttp"

val coroutinesVersion: String by project
val okHttpVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))
                implementation(project(":runtime:logging"))
                implementation(project(":runtime:io"))

                implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
                implementation("com.squareup.okhttp3:okhttp-coroutines:$okHttpVersion")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation(project(":runtime:hashing"))
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":runtime:testing"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}

val shadowTask = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    group = "shadow"
    // strip the default '-all' classifier, we won't need it since we publish this as a separate artifact
    archiveClassifier.set("")
    archiveAppendix.set("shaded")
    val main by kotlin.jvm().compilations
    from(main.output)
    configurations += main.compileDependencyFiles as Configuration
    configurations += main.runtimeDependencyFiles as Configuration

    dependencies {
        include {
            it.moduleGroup == "com.squareup.okhttp3"
        }
    }

    relocate("okhttp3", "aws.smithy.kotlin.shaded.okhttp3")
}

tasks.assemble.configure {
    dependsOn(shadowTask)
}

val shadedConfig = configurations.create("shaded") {
    attributes {
        attribute(
            Bundling.BUNDLING_ATTRIBUTE,
            objects.named(Bundling.SHADOWED)
        )
        attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements.JAR)
        )
        attribute(
            Category.CATEGORY_ATTRIBUTE,
            objects.named(Category.LIBRARY)
        )
        attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage.JAVA_RUNTIME)
        )
    }
    extendsFrom(configurations.getByName("jvmRuntimeClasspath"))

    // FIXME - can we get this configuration dependency set correct such that we don't have to filter it when generating the POM?

    // don't export as an outgoing variant
    isCanBeConsumed = false
    isCanBeResolved = false

}

artifacts {
    add(shadedConfig.name, shadowTask)
}

publishing {
    publications {

        create<MavenPublication>("shadow") {
            // There are some open bugs in shadow plugin that result in either broken OR
            // incorrect POMS that include the shaded deps:
            // https://github.com/johnrengelman/shadow/issues/634
            // https://github.com/johnrengelman/shadow/issues/807
            //
            // This prevents us from just doing something like:
            // project.extensions.configure<com.github.jengelman.gradle.plugins.shadow.ShadowExtension>{
            //     component(this@create)
            // }

            artifact(shadowTask)

            // Create a new artifact rather than using a classifier. We do this to get the right dependencies
            // in the generated pom file (if they shared GAV coordinates they would share the same POM file with
            // the shaded deps in it)
            artifactId = "$artifactId-shaded"
            pom.packaging = "jar"

            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")
                shadedConfig.allDependencies.forEach {
                    if ((it is ProjectDependency) || (it !is SelfResolvingDependency) && it.group != "com.squareup.okhttp3") {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", it.group)
                        dependencyNode.appendNode("artifactId", it.name)
                        dependencyNode.appendNode("version", it.version)
                        // FIXME - some of these are actually `compile` scope in the unshaded version...
                        dependencyNode.appendNode("scope", "runtime")
                    }
                }
            }
        }
    }
}