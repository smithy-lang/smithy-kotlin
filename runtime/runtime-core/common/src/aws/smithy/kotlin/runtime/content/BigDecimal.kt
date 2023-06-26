/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

public expect class BigDecimal(value: String) : Number {
    public fun toPlainString(): String
}
