/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.codegen.dsl.generateSmithyProjections
import aws.sdk.kotlin.gradle.codegen.dsl.smithyKotlinPlugin
import aws.sdk.kotlin.gradle.codegen.smithyKotlinProjectionPath
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    kotlin("jvm")
    alias(libs.plugins.aws.kotlin.repo.tools.smithybuild)
}

description = "Smithy protocol test suite"

skipPublishing()

data class ProtocolTest(val projectionName: String, val serviceShapeId: String, val sdkId: String? = null) {
    val packageName: String = projectionName.lowercase().filter { it.isLetterOrDigit() }
}

// The following section exposes Smithy protocol test suites as gradle test targets
// for the configured protocols in [enabledProtocols].
val enabledProtocols = listOf(
    ProtocolTest("aws-ec2-query", "aws.protocoltests.ec2#AwsEc2"),
    ProtocolTest("aws-json-10", "aws.protocoltests.json10#JsonRpc10"),
    ProtocolTest("aws-json-11", "aws.protocoltests.json#JsonProtocol"),
    ProtocolTest("aws-restjson", "aws.protocoltests.restjson#RestJson"),
    ProtocolTest("aws-restxml", "aws.protocoltests.restxml#RestXml"),
    ProtocolTest("aws-restxml-xmlns", "aws.protocoltests.restxml.xmlns#RestXmlWithNamespace"),
    ProtocolTest("aws-query", "aws.protocoltests.query#AwsQuery"),
    ProtocolTest("smithy-rpcv2-cbor", "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol"),

    // Custom hand written tests
    ProtocolTest("error-correction-json", "aws.protocoltests.errorcorrection#RequiredValueJson"),
    ProtocolTest("error-correction-xml", "aws.protocoltests.errorcorrection#RequiredValueXml"),
)

smithyBuild {
    enabledProtocols.forEach { test ->
        projections.register(test.projectionName) {
            imports = listOf(file("model").absolutePath)

            transforms = listOf(
                """
                {
                  "name": "includeServices",
                  "args": {
                    "services": ["${test.serviceShapeId}"]
                  }
                }
                """,
            )

            smithyKotlinPlugin {
                serviceShapeId = test.serviceShapeId
                packageName = "aws.sdk.kotlin.services.${test.packageName}"
                packageVersion = "1.0"
                sdkId = test.sdkId
                buildSettings {
                    generateFullProject = true
                    optInAnnotations = listOf(
                        "aws.smithy.kotlin.runtime.InternalApi",
                        "aws.sdk.kotlin.runtime.InternalSdkApi",
                    )
                }
                apiSettings {
                    defaultValueSerializationMode = "always"
                }
            }
        }
    }
}

val codegen by configurations.getting
dependencies {
    codegen(project(":codegen:smithy-aws-kotlin-codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)

    // NOTE: The protocol tests are published to maven as a jar, this ensures that
    // the aws-protocol-tests dependency is found when generating code such that the `includeServices` transform
    // actually works
    codegen(libs.smithy.aws.protocol.tests)
    codegen(libs.smithy.protocol.tests)
}

tasks.generateSmithyProjections {
    // ensure the generated clients use the same version of the runtime as the aws aws-runtime
    val sdkVersion: String by project
    val smithyKotlinRuntimeVersion = sdkVersion
    doFirst {
        System.setProperty("smithy.kotlin.codegen.clientRuntimeVersion", smithyKotlinRuntimeVersion)
    }
}

abstract class ProtocolTestTask @Inject constructor(private val project: Project) : DefaultTask() {
    /**
     * The projection
     */
    @get:Input
    abstract val projectionName: Property<String>

    /**
     * The projection root directory
     */
    @get:Input
    abstract val projectionRootDirectory: Property<String>

    @TaskAction
    fun runTests() {
        val projectionRootDir = project.file(projectionRootDirectory.get())
        println("[$projectionName] buildDir: $projectionRootDir")
        if (!projectionRootDir.exists()) {
            throw GradleException("$projectionRootDir does not exist")
        }
        val wrapper = if (System.getProperty("os.name").lowercase().contains("windows")) "gradlew.bat" else "gradlew"
        val gradlew = project.rootProject.file(wrapper).absolutePath

        // NOTE - this still requires us to publish to maven local.
        project.exec {
            workingDir = projectionRootDir
            executable = gradlew
            args = listOf("test")
        }
    }
}

smithyBuild.projections.forEach {
    val protocolName = it.name

    tasks.register<ProtocolTestTask>("testProtocol-$protocolName") {
        dependsOn(tasks.generateSmithyProjections)
        group = "Verification"
        projectionName.set(it.name)
        projectionRootDirectory.set(smithyBuild.smithyKotlinProjectionPath(it.name).map { it.toString() })
    }
}

tasks.register("testAllProtocols") {
    group = "Verification"
    val allTests = tasks.withType<ProtocolTestTask>()
    dependsOn(allTests)
}
