/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.logging

import software.aws.clientrt.util.InternalAPI

/**
 * Get the logger for the class [T]
 */
@InternalAPI
actual inline fun <reified T> platformGetLogger(): Logger =
    Logger.getLogger(requireNotNull(T::class.qualifiedName) { "getLogger<T> cannot be used on an anonymous object" })
