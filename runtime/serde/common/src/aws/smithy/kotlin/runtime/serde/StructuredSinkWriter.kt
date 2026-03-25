/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.io.SdkBufferedSink

public class StructuredSinkWriter(
    private val sink: SdkBufferedSink,
    private val settings: Settings = Settings.Default,
) {
    private var lastChar: Char = 0.toChar()
    private val indentUnit = " ".repeat(settings.indentSpaces)
    private var indentString: String = ""

    public fun newline() {
        if (settings.prettyPrint && lastChar != '\n') {
            sink.writeUtf8("\n")
            lastChar = '\n'
        }
    }

    private fun sinkWrite(text: String) {
        if (text.isNotEmpty()) {
            sink.writeUtf8(text)
            lastChar = text.last()
        }
    }

    public fun withBlock(start: String, end: String, block: StructuredSinkWriter.() -> Unit) {
        sinkWrite(start)
        indentString += indentUnit
        newline()
        block()
        newline()
        indentString = indentString.substring(0, indentString.length - indentUnit.length)
        sinkWrite(end)
    }

    public fun write(text: String) {
        if (settings.prettyPrint) {
            if (lastChar == '\n') {
                sinkWrite(indentString)
            }
        }
        sinkWrite(text)
    }

    public class Settings(builder: Builder) {
        public companion object {
            public val Default: Settings = Settings(Builder())
        }

        public val indentSpaces: Int = builder.indentSpaces
        public val prettyPrint: Boolean = builder.prettyPrint

        public class Builder {
            public var indentSpaces: Int = 2
            public var prettyPrint: Boolean = true
        }
    }
}
