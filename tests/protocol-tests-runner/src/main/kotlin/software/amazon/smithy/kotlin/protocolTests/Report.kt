/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.protocolTests

import software.amazon.smithy.kotlin.protocolTests.utils.JsonWriter
import java.time.Instant

/**
 * Writes the start of the protocol tests execution report.
 */
fun writeReportStart(writer: JsonWriter) {
    writer.startObject()
    writer.writeKvp("product", "AWS SDK for Kotlin")
    writer.writeKvp("model", "smithy")
    writer.writeKvp("sdkVersion", sdkVersion(writer))
    writer.writeKvp("date", Instant.now().toString())
    writer.writeKey("tags")
    writeTags(writer)
    writer.writeKey("suites")
    writer.startArray()
}

/**
 * Writes the end of the protocol tests execution report.
 */
fun writeReportEnd(writer: JsonWriter) {
    writer.endArray()
    writer.endObject()
}

/**
 * Writes the tags for the protocol tests report.
 */
fun writeTags(writer: JsonWriter) {
    writer.startObject()
    System.getProperty("os.name").let {
        writer.writeKvp("os.name", it)
    }
    System.getProperty("os.version").let {
        writer.writeKvp("os.version", it)
    }
    System.getProperty("os.arch").let {
        writer.writeKvp("os.arch", it)
    }
    System.getProperty("java.version").let {
        writer.writeKvp("java.version", it)
    }
    writer.endObject()
}

private fun sdkVersion(writer: JsonWriter): String {
    val sdkVersion = writer.javaClass.classLoader
        .getResourceAsStream("software/amazon/smithy/kotlin/codegen/core/sdk-version.txt")!!
        .readBytes().toString(Charsets.UTF_8)
    return sdkVersion
}
