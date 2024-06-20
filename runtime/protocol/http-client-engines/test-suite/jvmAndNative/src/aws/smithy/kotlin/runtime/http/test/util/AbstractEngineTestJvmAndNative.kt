/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.test.util

// FIXME jvmAndNative source set is not being configured properly.
// internal actual fun runBlockingTest(
//    context: CoroutineContext,
//    timeout: Duration?,
//    block: suspend CoroutineScope.() -> Unit,
// ) {
//    runBlocking(context) {
//        if (timeout != null) {
//            withTimeout(timeout) {
//                block()
//            }
//        } else {
//            block()
//        }
//    }
// }
