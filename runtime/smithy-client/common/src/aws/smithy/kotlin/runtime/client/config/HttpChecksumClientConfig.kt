package aws.smithy.kotlin.runtime.client.config

public interface HttpChecksumClientConfig {
    /**
     * todo
     */
    public val requestChecksumCalculation: RequestChecksumCalculation?

    public interface Builder {
        /**
         * todo
         */
        public var requestChecksumCalculation: RequestChecksumCalculation?
    }
}

public enum class RequestChecksumCalculation {
    /**
     * todo
     */
    WHEN_SUPPORTED,
    /**
     * todo
     */
    WHEN_REQUIRED,
}