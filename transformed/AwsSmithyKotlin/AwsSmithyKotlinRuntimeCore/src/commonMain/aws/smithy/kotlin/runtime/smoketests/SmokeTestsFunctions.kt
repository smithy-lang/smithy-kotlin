/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.smoketests

public expect fun exitProcess(status: Int): Nothing

public class SmokeTestsException(message: String) : Exception(message)

/**
 * An [Appendable] which can be used for printing test results to the console
 */
public val DefaultPrinter: Appendable = object : Appendable {
    override fun append(value: Char) = this.also { print(value) }
    override fun append(value: CharSequence?) = this.also { print(value) }
    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int) = this.also { print(value?.subSequence(startIndex, endIndex)) }
}
