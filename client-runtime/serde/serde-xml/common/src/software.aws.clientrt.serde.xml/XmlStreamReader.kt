package software.aws.clientrt.serde.xml

/**
 * Raw tokens produced when reading a XML document as a stream
 */
sealed class XmlToken {
    /**
     * Defines the name and namespace of an element
     */
    data class QualifiedName(val name: String, val namespace: String? = null)

    /**
     * The opening of an XML element
     */
    data class BeginElement(
        val id: QualifiedName,
        val attributes: Map<QualifiedName, String> = emptyMap()
    ) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))
        // Convenience constructor for name-only nodes with attributes.
        constructor(name: String, attributes: Map<QualifiedName, String>) : this(QualifiedName(name), attributes)
    }

    /**
     * The closing of an XML element
     */
    data class EndElement(val name: QualifiedName) : XmlToken() {
        // Convenience constructor for name-only nodes.
        constructor(name: String) : this(QualifiedName(name))
    }

    /**
     * An XML element text as string
     */
    data class Text(val value: String?) : XmlToken()

    /**
     * The end of the XML stream to signal that the XML-encoded value has no more
     * tokens
     */
    object EndDocument : XmlToken()

    override fun toString(): String = when (this) {
        is BeginElement -> "<${this.id}>"
        is EndElement -> "</${this.name}>"
        is Text -> "${this.value}"
        EndDocument -> "[EndDocument]"
    }
}

interface XmlStreamReader {

    /**
     *
     * @throws XmlGenerationException upon any error.
     */
    fun nextToken(): XmlToken

    /**
     * Recursively skip the next token. Meant for discarding unwanted/unrecognized nodes in an XML document
     */
    fun skipNext()

    /**
     * Peek at the next token type.  Successive calls will return the same value, meaning there is only one
     * look-ahead at any given time during the parsing of input data.
     */
    fun peek(): XmlToken

    /**
     * Return the current node depth of the parser.
     */
    fun currentDepth(): Int
}

/*
* Creates an [XmlStreamReader] instance
*/
internal expect fun xmlStreamReader(payload: ByteArray): XmlStreamReader
