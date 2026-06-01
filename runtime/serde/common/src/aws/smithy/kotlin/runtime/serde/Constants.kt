/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

/**
 * The maximum allowed depth of recursion when parsing data through serde. In practice, this limits the levels of
 * nesting of objects, maps, lists, and arrays. For example, a limit of `1_000` allows the following:
 *
 * ```json
 * {
 *   "obj1": {
 *     "obj2": {
 *       "obj3": {
 *         ...
 *           "obj1000": "foo"
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * But prohibits the following:
 *
 * ```json
 * {
 *   "obj1": {
 *     "obj2": {
 *       "obj3": {
 *         ...
 *           "obj1000": {
 *             "obj1001": "foo"
 *           }
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 */
public const val MAX_RECURSION_DEPTH: Int = 1_000
