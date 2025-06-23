package software.amazon.smithy.kotlin.protocolTests.utils

import java.io.OutputStreamWriter

enum class Result(val value: String) {
    PASSED("passed"),
    FAILED("failed"),
    ERRORED("errored"),
    SKIPPED("skipped"),
}

enum class TestType(val value: String) {
    REQUEST("request"),
    RESPONSE("response"),
}

data class TestResult(
    val testId: String,
    val testType: TestType,
    var result: Result = Result.PASSED,
    var log: String? = null,
)

fun writeResults(
    serviceId: String,
    protocolId: String,
    results: List<TestResult>,
) {
    JsonWriter(OutputStreamWriter(System.out)).use { writer ->
        writeResults(writer, serviceId, protocolId, results)
    }
}

fun writeResults(
    writer: JsonWriter,
    serviceId: String,
    protocolId: String,
    results: List<TestResult>,
) {
    writer.startObject()
    writer.writeKvp("service", serviceId)
    writer.writeKvp("protocol", protocolId)
    writer.writeKey("results")
    writer.startArray()
    for (result in results) {
        writeTestResult(writer, result)
    }
    writer.endArray()
    writer.endObject()
}

internal fun writeTestResult(writer: JsonWriter, result: TestResult) {
    writer.startObject()
        .writeKvp("id", result.testId)
        .writeKvp("type", result.testType.value)
        .writeKvp("result", result.result.value)
    result.log?.let {
        writer.writeKvp("log", it)
    }
    writer.endObject()
}
