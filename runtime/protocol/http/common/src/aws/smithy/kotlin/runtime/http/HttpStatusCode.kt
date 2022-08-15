/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

/**
 * Container for an HTTP status code
 */
public data class HttpStatusCode(public val value: Int, public val description: String) {
    // NOTE: data class over enum here to be forward compatible with potentially unknown status codes

    public enum class Category(private val range: IntRange) : Comparable<Category>, ClosedRange<Int> by range {
        INFORMATION(IntRange(100, 199)),
        SUCCESS(IntRange(200, 299)),
        REDIRECT(IntRange(300, 399)),
        CLIENT_ERROR(IntRange(400, 499)),
        SERVER_ERROR(IntRange(500, 599));

        public companion object {
            public fun fromCode(value: Int): Category =
                values().find { value in it.range } ?: error("Invalid HTTP code $value")
        }
    }

    override fun toString(): String = "$value: $description"
    override fun equals(other: Any?): Boolean = other is HttpStatusCode && other.value == value
    override fun hashCode(): Int = value.hashCode()

    public companion object {
        // If you add additional codes update the mapping

        // 1xx Informational
        public val Continue: HttpStatusCode = HttpStatusCode(100, "Continue")
        public val SwitchingProtocols: HttpStatusCode = HttpStatusCode(101, "Switching Protocols")
        public val Processing: HttpStatusCode = HttpStatusCode(102, "Processing")

        // 2xx Success
        public val OK: HttpStatusCode = HttpStatusCode(200, "OK")
        public val Created: HttpStatusCode = HttpStatusCode(201, "Created")
        public val Accepted: HttpStatusCode = HttpStatusCode(202, "Accepted")
        public val NonAuthoritativeInformation: HttpStatusCode = HttpStatusCode(203, "Non-Authoritative Information")
        public val NoContent: HttpStatusCode = HttpStatusCode(204, "No Content")
        public val ResetContent: HttpStatusCode = HttpStatusCode(205, "Reset Content")
        public val PartialContent: HttpStatusCode = HttpStatusCode(206, "Partial Content")
        public val MultiStatus: HttpStatusCode = HttpStatusCode(207, "Multi-Status")

        // 3xx Redirection
        public val MultipleChoices: HttpStatusCode = HttpStatusCode(300, "Multiple Choices")
        public val MovedPermanently: HttpStatusCode = HttpStatusCode(301, "Moved Permanently")
        public val Found: HttpStatusCode = HttpStatusCode(302, "Found")
        public val SeeOther: HttpStatusCode = HttpStatusCode(303, "See Other")
        public val NotModified: HttpStatusCode = HttpStatusCode(304, "Not Modified")
        public val UseProxy: HttpStatusCode = HttpStatusCode(305, "Use Proxy")
        public val TemporaryRedirect: HttpStatusCode = HttpStatusCode(307, "Temporary Redirect")
        public val PermanentRedirect: HttpStatusCode = HttpStatusCode(308, "Permanent Redirect")

        // 4xx Client Error
        public val BadRequest: HttpStatusCode = HttpStatusCode(400, "Bad Request")
        public val Unauthorized: HttpStatusCode = HttpStatusCode(401, "Unauthorized")
        public val PaymentRequired: HttpStatusCode = HttpStatusCode(402, "Payment Required")
        public val Forbidden: HttpStatusCode = HttpStatusCode(403, "Forbidden")
        public val NotFound: HttpStatusCode = HttpStatusCode(404, "Not Found")
        public val MethodNotAllowed: HttpStatusCode = HttpStatusCode(405, "Method Not Allowed")
        public val NotAcceptable: HttpStatusCode = HttpStatusCode(406, "Not Acceptable")
        public val ProxyAuthenticationRequired: HttpStatusCode = HttpStatusCode(407, "Proxy Authentication Required")
        public val RequestTimeout: HttpStatusCode = HttpStatusCode(408, "Request Timeout")
        public val Conflict: HttpStatusCode = HttpStatusCode(409, "Conflict")
        public val Gone: HttpStatusCode = HttpStatusCode(410, "Gone")
        public val LengthRequired: HttpStatusCode = HttpStatusCode(411, "Length Required")
        public val PreconditionFailed: HttpStatusCode = HttpStatusCode(412, "Precondition Failed")
        public val PayloadTooLarge: HttpStatusCode = HttpStatusCode(413, "Payload Too Large")
        public val RequestURITooLong: HttpStatusCode = HttpStatusCode(414, "Request-URI Too Long")
        public val UnsupportedMediaType: HttpStatusCode = HttpStatusCode(415, "Unsupported Media Type")
        public val RequestedRangeNotSatisfiable: HttpStatusCode = HttpStatusCode(416, "Requested Range Not Satisfiable")
        public val ExpectationFailed: HttpStatusCode = HttpStatusCode(417, "Expectation Failed")
        public val UnprocessableEntity: HttpStatusCode = HttpStatusCode(422, "Unprocessable Entity")
        public val Locked: HttpStatusCode = HttpStatusCode(423, "Locked")
        public val FailedDependency: HttpStatusCode = HttpStatusCode(424, "Failed Dependency")
        public val TooEarly: HttpStatusCode = HttpStatusCode(425, "Too Early")
        public val UpgradeRequired: HttpStatusCode = HttpStatusCode(426, "Upgrade Required")
        public val PreconditionRequired: HttpStatusCode = HttpStatusCode(428, "Precondition Required")
        public val TooManyRequests: HttpStatusCode = HttpStatusCode(429, "Too Many Requests")
        public val RequestHeaderFieldTooLarge: HttpStatusCode = HttpStatusCode(431, "Request Header Fields Too Large")
        public val UnavailableForLegalReason: HttpStatusCode = HttpStatusCode(451, "Unavailable For Legal Reason")

        // 5xx Server Error
        public val InternalServerError: HttpStatusCode = HttpStatusCode(500, "Internal Server Error")
        public val NotImplemented: HttpStatusCode = HttpStatusCode(501, "Not Implemented")
        public val BadGateway: HttpStatusCode = HttpStatusCode(502, "Bad Gateway")
        public val ServiceUnavailable: HttpStatusCode = HttpStatusCode(503, "Service Unavailable")
        public val GatewayTimeout: HttpStatusCode = HttpStatusCode(504, "Gateway Timeout")
        public val VersionNotSupported: HttpStatusCode = HttpStatusCode(505, "HTTP Version Not Supported")
        public val VariantAlsoNegotiates: HttpStatusCode = HttpStatusCode(506, "Variant Also Negotiates")
        public val InsufficientStorage: HttpStatusCode = HttpStatusCode(507, "Insufficient Storage")
        public val LoopDetected: HttpStatusCode = HttpStatusCode(508, "Loop Detected")
        public val NotExtended: HttpStatusCode = HttpStatusCode(510, "Not Extended")
        public val NetworkAuthenticationRequired: HttpStatusCode = HttpStatusCode(511, "Network Authentication Required")

        private val byValue: Map<Int, HttpStatusCode> = statusCodeMap()

        /**
         * Convert a raw status code integer to an [HttpStatusCode] instance
         */
        public fun fromValue(status: Int): HttpStatusCode =
            byValue[status] ?: HttpStatusCode(status, "Unknown HttpStatusCode")
    }
}

/**
 * Check if the given status code is a success code (HTTP codes 200 to 299 are considered successful)
 */
public fun HttpStatusCode.isSuccess(): Boolean = value in HttpStatusCode.Category.SUCCESS

/**
 * Check if the given status code is an informational code (HTTP codes 100 to 199 are considered informational)
 */
public fun HttpStatusCode.isInformational(): Boolean = value in HttpStatusCode.Category.INFORMATION

public fun HttpStatusCode.category(): HttpStatusCode.Category = HttpStatusCode.Category.fromCode(this.value)

private fun statusCodeMap(): Map<Int, HttpStatusCode> = mapOf(
    // 1xx Informational
    HttpStatusCode.Continue.value to HttpStatusCode.Continue,
    HttpStatusCode.SwitchingProtocols.value to HttpStatusCode.SwitchingProtocols,
    HttpStatusCode.Processing.value to HttpStatusCode.Processing,

    // 2xx Success
    HttpStatusCode.OK.value to HttpStatusCode.OK,
    HttpStatusCode.Created.value to HttpStatusCode.Created,
    HttpStatusCode.Accepted.value to HttpStatusCode.Accepted,
    HttpStatusCode.NonAuthoritativeInformation.value to HttpStatusCode.NonAuthoritativeInformation,
    HttpStatusCode.NoContent.value to HttpStatusCode.NoContent,
    HttpStatusCode.ResetContent.value to HttpStatusCode.ResetContent,
    HttpStatusCode.PartialContent.value to HttpStatusCode.PartialContent,
    HttpStatusCode.MultiStatus.value to HttpStatusCode.MultiStatus,

    // 3xx Redirection
    HttpStatusCode.MultipleChoices.value to HttpStatusCode.MultipleChoices,
    HttpStatusCode.MovedPermanently.value to HttpStatusCode.MovedPermanently,
    HttpStatusCode.Found.value to HttpStatusCode.Found,
    HttpStatusCode.SeeOther.value to HttpStatusCode.SeeOther,
    HttpStatusCode.NotModified.value to HttpStatusCode.NotModified,
    HttpStatusCode.UseProxy.value to HttpStatusCode.UseProxy,
    HttpStatusCode.TemporaryRedirect.value to HttpStatusCode.TemporaryRedirect,
    HttpStatusCode.PermanentRedirect.value to HttpStatusCode.PermanentRedirect,

    // 4xx Client Error
    HttpStatusCode.BadRequest.value to HttpStatusCode.BadRequest,
    HttpStatusCode.Unauthorized.value to HttpStatusCode.Unauthorized,
    HttpStatusCode.PaymentRequired.value to HttpStatusCode.PaymentRequired,
    HttpStatusCode.Forbidden.value to HttpStatusCode.Forbidden,
    HttpStatusCode.NotFound.value to HttpStatusCode.NotFound,
    HttpStatusCode.MethodNotAllowed.value to HttpStatusCode.MethodNotAllowed,
    HttpStatusCode.NotAcceptable.value to HttpStatusCode.NotAcceptable,
    HttpStatusCode.ProxyAuthenticationRequired.value to HttpStatusCode.ProxyAuthenticationRequired,
    HttpStatusCode.RequestTimeout.value to HttpStatusCode.RequestTimeout,
    HttpStatusCode.Conflict.value to HttpStatusCode.Conflict,
    HttpStatusCode.Gone.value to HttpStatusCode.Gone,
    HttpStatusCode.LengthRequired.value to HttpStatusCode.LengthRequired,
    HttpStatusCode.PreconditionFailed.value to HttpStatusCode.PreconditionFailed,
    HttpStatusCode.PayloadTooLarge.value to HttpStatusCode.PayloadTooLarge,
    HttpStatusCode.RequestURITooLong.value to HttpStatusCode.RequestURITooLong,
    HttpStatusCode.UnsupportedMediaType.value to HttpStatusCode.UnsupportedMediaType,
    HttpStatusCode.RequestedRangeNotSatisfiable.value to HttpStatusCode.RequestedRangeNotSatisfiable,
    HttpStatusCode.ExpectationFailed.value to HttpStatusCode.ExpectationFailed,
    HttpStatusCode.UnprocessableEntity.value to HttpStatusCode.UnprocessableEntity,
    HttpStatusCode.Locked.value to HttpStatusCode.Locked,
    HttpStatusCode.FailedDependency.value to HttpStatusCode.FailedDependency,
    HttpStatusCode.TooEarly.value to HttpStatusCode.TooEarly,
    HttpStatusCode.UpgradeRequired.value to HttpStatusCode.UpgradeRequired,
    HttpStatusCode.PreconditionRequired.value to HttpStatusCode.PreconditionRequired,
    HttpStatusCode.TooManyRequests.value to HttpStatusCode.TooManyRequests,
    HttpStatusCode.RequestHeaderFieldTooLarge.value to HttpStatusCode.RequestHeaderFieldTooLarge,
    HttpStatusCode.UnavailableForLegalReason.value to HttpStatusCode.UnavailableForLegalReason,

    // 5xx Server Error
    HttpStatusCode.InternalServerError.value to HttpStatusCode.InternalServerError,
    HttpStatusCode.NotImplemented.value to HttpStatusCode.NotImplemented,
    HttpStatusCode.BadGateway.value to HttpStatusCode.BadGateway,
    HttpStatusCode.ServiceUnavailable.value to HttpStatusCode.ServiceUnavailable,
    HttpStatusCode.GatewayTimeout.value to HttpStatusCode.GatewayTimeout,
    HttpStatusCode.VersionNotSupported.value to HttpStatusCode.VersionNotSupported,
    HttpStatusCode.VariantAlsoNegotiates.value to HttpStatusCode.VariantAlsoNegotiates,
    HttpStatusCode.InsufficientStorage.value to HttpStatusCode.InsufficientStorage,
    HttpStatusCode.LoopDetected.value to HttpStatusCode.LoopDetected,
    HttpStatusCode.NotExtended.value to HttpStatusCode.NotExtended,
    HttpStatusCode.NetworkAuthenticationRequired.value to HttpStatusCode.NetworkAuthenticationRequired,
)
