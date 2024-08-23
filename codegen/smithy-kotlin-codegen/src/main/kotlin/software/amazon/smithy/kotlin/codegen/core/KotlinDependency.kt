/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.utils.StringUtils

// root namespace for the runtime
const val RUNTIME_ROOT_NS = "aws.smithy.kotlin.runtime"

/**
 * Test if a string represents a valid artifact version string
 */
fun isValidVersion(version: String): Boolean {
    val re = Regex("\\d+\\.\\d+\\.\\d[a-z0-9A-Z.-]*\$")
    return re.matches(version)
}

private fun getDefaultRuntimeVersion(): String {
    // generated as part of the build, see smithy-kotlin-codegen/build.gradle.kts
    try {
        val version = object {}.javaClass.getResource("sdk-version.txt")?.readText() ?: throw CodegenException("sdk-version.txt does not exist")
        check(isValidVersion(version)) { "Version parsed from sdk-version.txt '$version' is not a valid version string" }
        return version
    } catch (ex: Exception) {
        throw CodegenException("failed to load sdk-version.txt which sets the default client-runtime version", ex)
    }
}

// publishing info
const val RUNTIME_GROUP: String = "aws.smithy.kotlin"
val RUNTIME_VERSION: String = System.getProperty("smithy.kotlin.codegen.clientRuntimeVersion", getDefaultRuntimeVersion())
val KOTLIN_COMPILER_VERSION: String = System.getProperty("smithy.kotlin.codegen.kotlinCompilerVersion", "2.0.10")

enum class SourceSet {
    CommonMain,
    CommonTest,
    ;

    override fun toString(): String = StringUtils.uncapitalize(name)
}

// See: https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph
enum class GradleConfiguration(val sourceSet: SourceSet) {
    // purely internal and not meant to be exposed to consumers.
    Implementation(SourceSet.CommonMain),

    // transitively exported to consumers, for compile.
    Api(SourceSet.CommonMain),

    // only required at compile time, but should not leak into the runtime
    CompileOnly(SourceSet.CommonMain),

    // only required at runtime
    RuntimeOnly(SourceSet.CommonMain),

    // internal test
    TestImplementation(SourceSet.CommonTest),

    // compile time test only
    TestCompileOnly(SourceSet.CommonTest),

    // compile time runtime only
    TestRuntimeOnly(SourceSet.CommonTest),
    ;

    override fun toString(): String = StringUtils.uncapitalize(this.name)

    /**
     * Return true if the dependency is in the test scope
     */
    val isTestScope
        get() = sourceSet == SourceSet.CommonTest

    /**
     * The name of the dependency type in Gradle when used in a KMP project. For instance, [TestImplementation] would be
     * called `implementation` when used in the `commonTest` source set for KMP (vs `testImplementation` when used in a
     * non-KMP project).
     */
    val kmpName: String
        get() = when {
            isTestScope -> name.removePrefix("Test")
            else -> name
        }.let(StringUtils::uncapitalize)
}

data class KotlinDependency(
    val config: GradleConfiguration,
    val namespace: String,
    val group: String,
    val artifact: String,
    val version: String,
) : SymbolDependencyContainer {

    companion object {
        // AWS managed dependencies
        val CORE = KotlinDependency(GradleConfiguration.Api, RUNTIME_ROOT_NS, RUNTIME_GROUP, "runtime-core", RUNTIME_VERSION)
        val HTTP = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.http", RUNTIME_GROUP, "http", RUNTIME_VERSION)
        val HTTP_CLIENT = KotlinDependency(GradleConfiguration.Api, "$RUNTIME_ROOT_NS.http", RUNTIME_GROUP, "http-client", RUNTIME_VERSION)
        val SERDE = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.serde", RUNTIME_GROUP, "serde", RUNTIME_VERSION)
        val SERDE_JSON = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.serde.json", RUNTIME_GROUP, "serde-json", RUNTIME_VERSION)
        val SERDE_XML = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.serde.xml", RUNTIME_GROUP, "serde-xml", RUNTIME_VERSION)
        val SERDE_FORM_URL = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.serde.formurl", RUNTIME_GROUP, "serde-form-url", RUNTIME_VERSION)
        val SERDE_CBOR = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.serde.cbor", RUNTIME_GROUP, "serde-cbor", RUNTIME_VERSION)
        val SMITHY_CLIENT = KotlinDependency(GradleConfiguration.Api, "$RUNTIME_ROOT_NS.client", RUNTIME_GROUP, "smithy-client", RUNTIME_VERSION)
        val SMITHY_TEST = KotlinDependency(GradleConfiguration.TestImplementation, "$RUNTIME_ROOT_NS.smithy.test", RUNTIME_GROUP, "smithy-test", RUNTIME_VERSION)
        val DEFAULT_HTTP_ENGINE = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.http.engine", RUNTIME_GROUP, "http-client-engine-default", RUNTIME_VERSION)
        val AWS_CRT_HTTP_ENGINE = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.http.engine.crt", RUNTIME_GROUP, "http-client-engine-crt", RUNTIME_VERSION)
        val AWS_CREDENTIALS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.auth.awscredentials", RUNTIME_GROUP, "aws-credentials", RUNTIME_VERSION)
        val AWS_SIGNING_COMMON = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.auth.awssigning", RUNTIME_GROUP, "aws-signing-common", RUNTIME_VERSION)
        val AWS_SIGNING_DEFAULT = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.auth.awssigning", RUNTIME_GROUP, "aws-signing-default", RUNTIME_VERSION)
        val TELEMETRY_API = KotlinDependency(GradleConfiguration.Api, "$RUNTIME_ROOT_NS.telemetry", RUNTIME_GROUP, "telemetry-api", RUNTIME_VERSION)
        val TELEMETRY_DEFAULTS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.telemetry", RUNTIME_GROUP, "telemetry-defaults", RUNTIME_VERSION)

        val AWS_JSON_PROTOCOLS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol.json", RUNTIME_GROUP, "aws-json-protocols", RUNTIME_VERSION)
        val AWS_EVENT_STREAM = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol.eventstream", RUNTIME_GROUP, "aws-event-stream", RUNTIME_VERSION)
        val AWS_PROTOCOL_CORE = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol", RUNTIME_GROUP, "aws-protocol-core", RUNTIME_VERSION)
        val AWS_XML_PROTOCOLS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol.xml", RUNTIME_GROUP, "aws-xml-protocols", RUNTIME_VERSION)
        val HTTP_AUTH = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.http.auth", RUNTIME_GROUP, "http-auth", RUNTIME_VERSION)
        val HTTP_AUTH_AWS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.http.auth", RUNTIME_GROUP, "http-auth-aws", RUNTIME_VERSION)
        val IDENTITY_API = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS", RUNTIME_GROUP, "identity-api", RUNTIME_VERSION)
        val SMITHY_RPCV2_PROTOCOLS = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol.rpcv2", RUNTIME_GROUP, "smithy-rpcv2-protocols", RUNTIME_VERSION)
        val SMITHY_RPCV2_PROTOCOLS_CBOR = KotlinDependency(GradleConfiguration.Implementation, "$RUNTIME_ROOT_NS.awsprotocol.rpcv2.cbor", RUNTIME_GROUP, "smithy-rpcv2-protocols", RUNTIME_VERSION)

        // External third-party dependencies
        val KOTLIN_STDLIB = KotlinDependency(GradleConfiguration.Implementation, "kotlin", "org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN_COMPILER_VERSION)
        val KOTLIN_TEST = KotlinDependency(GradleConfiguration.TestImplementation, "kotlin.test", "org.jetbrains.kotlin", "kotlin-test", KOTLIN_COMPILER_VERSION)
    }

    override fun getDependencies(): List<SymbolDependency> {
        val dependency = SymbolDependency.builder()
            .dependencyType(config.name)
            .packageName(namespace)
            .version(version)
            .putProperty("dependency", this)
            .build()
        return listOf(dependency)
    }
}

abstract class RuntimeTypePackage(
    val dependency: KotlinDependency,
    val defaultSubpackage: String = "",
) {
    /**
     * Create a symbol named by [name] from the [RuntimeTypePackage].
     * @param name the name of the symbol
     * @param subpackage the subpackage from the [dependency] namespace, defaults to [defaultSubpackage]
     * @param isExtension flag indicating this is an extension to [Symbol]
     * @param nullable flag indicating if this symbol is nullable or not (NOTE: Nullability is generally inferred only
     * on member symbols).
     */
    fun symbol(
        name: String,
        subpackage: String = defaultSubpackage,
        isExtension: Boolean = false,
        nullable: Boolean = true,
    ): Symbol = buildSymbol {
        this.name = name
        namespace(dependency, subpackage)
        this.isExtension = isExtension
        this.nullable = nullable
    }
}
