/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

internal actual fun xmlStreamWriter(pretty: Boolean): XmlStreamWriter = SimpleXmlStreamWriter(pretty)
