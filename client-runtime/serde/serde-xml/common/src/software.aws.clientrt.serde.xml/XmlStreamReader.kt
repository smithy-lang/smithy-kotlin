/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

interface XmlStreamReader {

    /**
     *
     * @throws XmlGenerationException upon any error.
     */
    suspend fun nextToken(): XmlToken

    /**
     * Recursively skip the next token. Meant for discarding unwanted/unrecognized nodes in an XML document
     */
    suspend fun skipNext()

    /**
     * Return the last token to be taken
     */
    val currentToken: XmlToken

    /**
     * Peek at the next token type.  Successive calls will return the same value, meaning there is only one
     * look-ahead at any given time during the parsing of input data.
     */
    suspend fun peek(): XmlToken

    /**
     * Return the current node depth of the parser.
     */
    val currentDepth: Int
}

/*
* Creates an [XmlStreamReader] instance
*/
internal expect fun xmlStreamReader(payload: ByteArray): XmlStreamReader
