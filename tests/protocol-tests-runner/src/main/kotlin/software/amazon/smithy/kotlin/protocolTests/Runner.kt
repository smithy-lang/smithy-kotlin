/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.protocolTests

import software.amazon.smithy.kotlin.protocolTests.utils.JsonWriter
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.traits.TagsTrait
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Assumes each argument in args to be a JAR file defining protocol tests. Each argument is passed as-is to the
 * Smithy model assembler, and then, for each valid service defining protocol tests we
 *
 * 1. Generate the protocol tests
 * 2. Build the generated protocol tests
 * 3. Execute the protocol tests
 * 4. Collect the output of the service into the final result
 *
 * The final report that aggregates the results for each suite (client) found in the model is stored
 * in a file `results.json`.
 */
fun main(args: Array<String>) {
    val assembler = Model
        .assembler()
        .discoverModels()
    for (arg in args) {
        assembler.addImport(arg)
    }
    val model = assembler.assemble().unwrap()
    PrintWriter("result.json").use { out ->
        val jsonWriter = JsonWriter(out)
        writeReportStart(jsonWriter)
        for (service in model.serviceShapes) {
            if (hasStandardProtocolTests(service, model)) {
                val codegenPath = Files.createTempDirectory("protocol-tests-${service.id.name}")
                // Generate the client and protocol tests assertions
                generateProtocolTests(model, service!!, codegenPath)
                // Build the client and protocol tests
                buildProtocolTests(codegenPath)
                // Run the protocol tests and get the results
                val results = runProtocolTests(codegenPath);
                // Add the result to the final report
                jsonWriter.writeEncodedValue(results)
            }
        }
        writeReportEnd(jsonWriter)
    }
}

private fun hasStandardProtocolTests(service: ServiceShape, model: Model): Boolean {
    val isAwsServiceTest = service.findTrait(TagsTrait.ID)
        .map { trait -> TagsTrait::class.java.cast(trait) }
        // We add a tag to protocol tests for specific AWS services
        // that require customizations to run properly. Those
        // not standard protocol tests are filtered out here.
        .map { tags -> tags.values.contains("aws-service-test") }
        .orElse(false)

    if (isAwsServiceTest) {
        return false
    }
    // Check that at least one operation in the service has a test trait, either for
    // requests or for responses.
    TopDownIndex.of(model).getContainedOperations(service).forEach { operation ->
        if (operation.hasTrait(HttpResponseTestsTrait.ID)
            || operation.hasTrait(HttpRequestTestsTrait.ID)
        ) {
            return true
        }
    }
    return false
}

/**
 * Executes `gradle build -x test` in the given directory to build the generated
 * code that includes the client and the protocol tests. This build is expected
 * to generate an uber JAR that we can the call directly without having to setup
 * all the classpath elements.
 */
private fun buildProtocolTests(codegenPath: Path) {
    ProcessBuilder("gradle", "build", "-x", "test")
        .directory(codegenPath.toFile())
        .inheritIO()
        .start()
        .waitFor()
}

/**
 * After building the protocol tests and client source files the uber jar is found inside
 * `build/libs/`. We now just run java with this jar as an argument. The report with the
 * results will be printed out to the standard output, we capture it here and return it
 * to fill up the final report.
 */
private fun runProtocolTests(codegenPath: Path): String {
    val name = codegenPath.toFile().name;
    val process = ProcessBuilder("java", "-jar", "build/libs/${name}-all.jar")
        .directory(codegenPath.toFile())
        .start()
    val writer = StringWriter()
    InputStreamReader(process.inputStream, StandardCharsets.UTF_8).transferTo(writer)
    process.waitFor()
    return writer.toString()
}
