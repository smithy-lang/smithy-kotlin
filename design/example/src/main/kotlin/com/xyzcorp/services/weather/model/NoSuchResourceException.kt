package com.xyzcorp.services.weather.model

import com.amazonaws.smithy.runtime.ServiceException

class NoSuchResourceException : ServiceException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    override val errorType: ErrorType = ErrorType.Client

    var resourceType: String? = null

}