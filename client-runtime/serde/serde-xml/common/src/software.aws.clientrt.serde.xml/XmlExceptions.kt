// ktlint-disable filename
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.SerializationException

class XmlGenerationException(t: Throwable) : SerializationException(t)
