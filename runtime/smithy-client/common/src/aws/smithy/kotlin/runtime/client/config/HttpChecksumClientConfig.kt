package aws.smithy.kotlin.runtime.client.config

/**
 * Client config for HTTP checksums
 */
public interface HttpChecksumClientConfig {
    /**
     * Configures request checksum calculation
     */
    public val requestChecksumCalculation: ChecksumConfigOption?

    /**
     * Configures response checksum validation
     */
    public val responseChecksumValidation: ChecksumConfigOption?

    public interface Builder {
        /**
         * Configures request checksum calculation
         */
        public var requestChecksumCalculation: ChecksumConfigOption?

        /**
         * Configures response checksum validation
         */
        public var responseChecksumValidation: ChecksumConfigOption?
    }
}

public enum class ChecksumConfigOption {
    /**
     * SDK will create/validate checksum if the service marks it as required or if this is set.
     */
    WHEN_SUPPORTED,

    /**
     * SDK will only create/validate checksum if the service marks it as required.
     */
    WHEN_REQUIRED,
}
