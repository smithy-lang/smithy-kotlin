/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http

/**
 * Container for an HTTP status code
 */
data class HttpStatusCode(val value: Int, val description: String) {
    // NOTE: data class over enum here to be forward compatible with potentially unknown status codes

    override fun toString(): String = "$value: $description"
    override fun equals(other: Any?): Boolean = other is HttpStatusCode && other.value == value
    override fun hashCode(): Int = value.hashCode()

    companion object {
        // If you add additional codes update the mapping

        // 1xx Informational
        val Continue = HttpStatusCode(100, "Continue")
        val SwitchingProtocols = HttpStatusCode(101, "Switching Protocols")
        val Processing = HttpStatusCode(102, "Processing")

        // 2xx Success
        val OK = HttpStatusCode(200, "OK")
        val Created = HttpStatusCode(201, "Created")
        val Accepted = HttpStatusCode(202, "Accepted")
        val NonAuthoritativeInformation = HttpStatusCode(203, "Non-Authoritative Information")
        val NoContent = HttpStatusCode(204, "No Content")
        val ResetContent = HttpStatusCode(205, "Reset Content")
        val PartialContent = HttpStatusCode(206, "Partial Content")
        val MultiStatus = HttpStatusCode(207, "Multi-Status")

        // 3xx Redirection
        val MultipleChoices = HttpStatusCode(300, "Multiple Choices")
        val MovedPermanently = HttpStatusCode(301, "Moved Permanently")
        val Found = HttpStatusCode(302, "Found")
        val SeeOther = HttpStatusCode(303, "See Other")
        val NotModified = HttpStatusCode(304, "Not Modified")
        val UseProxy = HttpStatusCode(305, "Use Proxy")
        val TemporaryRedirect = HttpStatusCode(307, "Temporary Redirect")
        val PermanentRedirect = HttpStatusCode(308, "Permanent Redirect")

        // 4xx Client Error
        val BadRequest = HttpStatusCode(400, "Bad Request")
        val Unauthorized = HttpStatusCode(401, "Unauthorized")
        val PaymentRequired = HttpStatusCode(402, "Payment Required")
        val Forbidden = HttpStatusCode(403, "Forbidden")
        val NotFound = HttpStatusCode(404, "Not Found")
        val MethodNotAllowed = HttpStatusCode(405, "Method Not Allowed")
        val NotAcceptable = HttpStatusCode(406, "Not Acceptable")
        val ProxyAuthenticationRequired = HttpStatusCode(407, "Proxy Authentication Required")
        val RequestTimeout = HttpStatusCode(408, "Request Timeout")
        val Conflict = HttpStatusCode(409, "Conflict")
        val Gone = HttpStatusCode(410, "Gone")
        val LengthRequired = HttpStatusCode(411, "Length Required")
        val PreconditionFailed = HttpStatusCode(412, "Precondition Failed")
        val PayloadTooLarge = HttpStatusCode(413, "Payload Too Large")
        val RequestURITooLong = HttpStatusCode(414, "Request-URI Too Long")
        val UnsupportedMediaType = HttpStatusCode(415, "Unsupported Media Type")
        val RequestedRangeNotSatisfiable = HttpStatusCode(416, "Requested Range Not Satisfiable")
        val ExpectationFailed = HttpStatusCode(417, "Expectation Failed")
        val UnprocessableEntity = HttpStatusCode(422, "Unprocessable Entity")
        val Locked = HttpStatusCode(423, "Locked")
        val FailedDependency = HttpStatusCode(424, "Failed Dependency")
        val TooEarly = HttpStatusCode(425, "Too Early")
        val UpgradeRequired = HttpStatusCode(426, "Upgrade Required")
        val PreconditionRequired = HttpStatusCode(428, "Precondition Required")
        val TooManyRequests = HttpStatusCode(429, "Too Many Requests")
        val RequestHeaderFieldTooLarge = HttpStatusCode(431, "Request Header Fields Too Large")
        val UnavailableForLegalReason = HttpStatusCode(451, "Unavailable For Legal Reason")

        // 5xx Server Error
        val InternalServerError = HttpStatusCode(500, "Internal Server Error")
        val NotImplemented = HttpStatusCode(501, "Not Implemented")
        val BadGateway = HttpStatusCode(502, "Bad Gateway")
        val ServiceUnavailable = HttpStatusCode(503, "Service Unavailable")
        val GatewayTimeout = HttpStatusCode(504, "Gateway Timeout")
        val VersionNotSupported = HttpStatusCode(505, "HTTP Version Not Supported")
        val VariantAlsoNegotiates = HttpStatusCode(506, "Variant Also Negotiates")
        val InsufficientStorage = HttpStatusCode(507, "Insufficient Storage")
        val LoopDetected = HttpStatusCode(508, "Loop Detected")
        val NotExtended = HttpStatusCode(510, "Not Extended")
        val NetworkAuthenticationRequired = HttpStatusCode(511, "Network Authentication Required")

        private val byValue: Map<Int, HttpStatusCode> = statusCodeMap()

        /**
         * Convert a raw status code integer to an [HttpStatusCode] instance
         */
        fun fromValue(status: Int): HttpStatusCode {
            return byValue[status] ?: HttpStatusCode(status, "Unknown HttpStatusCode")
        }
    }
}

/**
 * Check if the given status code is a success code (HTTP codes 200 to 299 are considered successful)
 */
fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

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
    HttpStatusCode.NetworkAuthenticationRequired.value to HttpStatusCode.NetworkAuthenticationRequired
)
