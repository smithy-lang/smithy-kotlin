package aws.smithy.kotlin.runtime.client.config

/**
 * Client config for HTTP checksums
 */
public interface HttpChecksumClientConfig {
    /**
     * Configures request checksum calculation
     */
    public val requestChecksumCalculation: HttpChecksumConfigOption?

    /**
     * Configures response checksum validation
     */
    public val responseChecksumValidation: HttpChecksumConfigOption?

    public interface Builder {
        /**
         * Configures request checksum calculation
         */
        public var requestChecksumCalculation: HttpChecksumConfigOption?

        /**
         * Configures response checksum validation
         */
        public var responseChecksumValidation: HttpChecksumConfigOption?
    }
}

public enum class HttpChecksumConfigOption {
    /**
     * SDK will calculate/validate checksum if the service marks it as required or if the service offers optional checksums.
     */
    WHEN_SUPPORTED,

    /**
     * SDK will only calculate/validate checksum if the service marks it as required.
     */
    WHEN_REQUIRED,
}
