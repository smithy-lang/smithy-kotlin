/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.traits.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeProvider

// Without a protocol generator there are certain components expected to exist that aren't getting generated.
// Fill in the types manually so that the generated code compiles. None of these are needed for the tests at hand.
interface LambdaAuthSchemeParameters
interface LambdaAuthSchemeProvider : AuthSchemeProvider<LambdaAuthSchemeParameters>
