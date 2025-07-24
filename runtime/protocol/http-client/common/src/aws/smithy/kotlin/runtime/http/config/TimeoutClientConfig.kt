package aws.smithy.kotlin.runtime.http.config

import kotlin.time.Duration

/**
 * Defines optional timeout configuration for clients.
 */
public interface TimeoutClientConfig {
    /**
     * The maximum amount of time to wait for any single attempt of a request within the retry loop. By default, the
     * value is `null` indicating no timeout is enforced.
     */
    public val attemptTimeout: Duration?

    /**
     * The maximum amount of time to wait for completion of a call, including any retries after the first attempt. By
     * default, the value is `null` indicating no timeout is enforced.
     */
    public val callTimeout: Duration?

    /**
     * A mutable instance used to set timeout configuration for clients.
     */
    public interface Builder {
        /**
         * The maximum amount of time to wait for any single attempt of a request within the retry loop. By default, the
         * value is `null` indicating no timeout is enforced.
         */
        public var attemptTimeout: Duration?

        /**
         * The maximum amount of time to wait for completion of a call, including any retries after the first attempt.
         * By default, the value is `null` indicating no timeout is enforced.
         */
        public var callTimeout: Duration?
    }
}
