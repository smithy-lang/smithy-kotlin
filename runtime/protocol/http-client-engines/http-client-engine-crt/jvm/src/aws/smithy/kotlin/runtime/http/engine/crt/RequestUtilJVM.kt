/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

// FIXME Add support for bidirectional streaming to aws-crt-kotlin's Java bindings
// https://github.com/awslabs/aws-crt-java/pull/420
internal actual val canSendDuplexBody: Boolean = false