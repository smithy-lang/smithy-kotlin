/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.smoketests

import kotlin.system.exitProcess

public actual fun exitProcess(status: Int): Nothing = exitProcess(status)
