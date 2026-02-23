/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.testing

// Note: We can't just use kotlin.test.BeforeClass / kotlin.test.AfterClass because
// those are only available in Native, not Common: https://youtrack.jetbrains.com/issue/KT-49141
/**
 * Marks a function to run once before all tests in a class
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public expect annotation class BeforeAll()

/**
 * Marks a function to run once after all tests in a class
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public expect annotation class AfterAll()

/**
 * Marks a test which should be ignored on Native targets only
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public expect annotation class IgnoreNative()
