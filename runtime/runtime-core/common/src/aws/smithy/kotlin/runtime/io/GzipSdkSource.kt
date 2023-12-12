/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

/**
 * Wraps the SdkSource so that it compresses into gzip format with each read.
 */
public expect class GzipSdkSource(source: SdkSource) : SdkSource
