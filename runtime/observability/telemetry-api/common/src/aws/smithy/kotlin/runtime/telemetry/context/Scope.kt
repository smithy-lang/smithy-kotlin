/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.context

import aws.smithy.kotlin.runtime.io.Closeable

/**
 * Delineates a logical scope that has a beginning and end (e.g. a function)
 */
public interface Scope : Closeable
