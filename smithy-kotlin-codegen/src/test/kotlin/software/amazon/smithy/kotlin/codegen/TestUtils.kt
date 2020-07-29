/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions

fun String.shouldSyntacticSanityCheck() {
    // sanity check since we are testing fragments
    var openBraces = 0
    var closedBraces = 0
    var openParens = 0
    var closedParens = 0
    this.forEach {
        when (it) {
            '{' -> openBraces++
            '}' -> closedBraces++
            '(' -> openParens++
            ')' -> closedParens++
        }
    }
    Assertions.assertEquals(openBraces, closedBraces, "unmatched open/closed braces:\n$this")
    Assertions.assertEquals(openParens, closedParens, "unmatched open/close parens:\n$this")
}
