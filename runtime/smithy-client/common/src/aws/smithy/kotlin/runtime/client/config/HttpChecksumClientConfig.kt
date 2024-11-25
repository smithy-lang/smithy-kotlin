package aws.smithy.kotlin.runtime.client.config

/**
 * todo
 */
public interface HttpChecksumClientConfig {
    /**
     * todo
     */
    public val requestChecksumCalculation: ChecksumConfigOption?

    /**
     * todo
     */
    public val responseChecksumValidation: ChecksumConfigOption?

    public interface Builder {
        /**
         * todo
         */
        public var requestChecksumCalculation: ChecksumConfigOption?

        /**
         * todo
         */
        public var responseChecksumValidation: ChecksumConfigOption?
    }
}

public enum class ChecksumConfigOption {
    /**
     * todo
     */
    WHEN_SUPPORTED,

    /**
     * todo
     */
    WHEN_REQUIRED,
}
