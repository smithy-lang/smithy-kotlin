/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.testing

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public actual annotation class BeforeAll

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public actual annotation class AfterAll

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public actual annotation class IgnoreNative

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public actual annotation class TestInstance(actual val value: TestLifecycle)

public actual enum class TestLifecycle {
    PER_CLASS,
    PER_METHOD,
}
