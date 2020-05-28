package software.aws.clientrt.http

/**
 * Represents a wire protocol
 * @property protocolName name of protocol
 * @property defaultPort default port for the protocol
 */
enum class Protocol(val protocolName: String, val defaultPort: Int) {
    /**
     * HTTPS over port 443
     */
    HTTPS("https", 443),

    /**
     * HTTP over port 80
     */
    HTTP("http", 80),

    /**
     * WebSocket over port 80
     */
    WS("ws", 80),

    /**
     * Secure WebSocket over port 443
     */
    WSS("wss", 443),
}
