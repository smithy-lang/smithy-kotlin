/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.testing

public actual typealias BeforeAll = kotlin.test.BeforeClass

public actual typealias AfterAll = kotlin.test.AfterClass

public actual typealias IgnoreNative = kotlin.test.Ignore

public actual typealias TestInstance = kotlin.test.TestInstance

// Effectively ignored on K/N. This is fine because we only use this to enable non-static @BeforeAll/@AfterAll methods
// on JVM.
public actual annotation class TestInstance(val value: TestLifecycle)

public actual enum class TestLifecycle {
    PER_CLASS,
    PER_METHOD,
}
