/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.smoketests

import aws.smithy.kotlin.runtime.DeprecatedUntilVersion

public expect fun exitProcess(status: Int): Nothing

@DeprecatedUntilVersion(1, 6)
public class SmokeTestsException(message: String) : Exception(message)

/**
 * An [Appendable] which can be used for printing test results to the console
 */
public val DefaultPrinter: Appendable = object : Appendable {
    override fun append(c: Char) = this.also { print(c) }
    override fun append(csq: CharSequence?) = this.also { print(csq) }
    override fun append(csq: CharSequence?, start: Int, end: Int) = this.also { print(csq?.subSequence(start, end)) }
}
