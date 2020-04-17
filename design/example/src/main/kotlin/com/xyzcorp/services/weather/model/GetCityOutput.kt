package com.xyzcorp.services.weather.model

import com.amazonaws.smithy.runtime.Document

class GetCityOutput {

    var name: String? = null

    var coordinates: CityCoordinates? = null

    var city: CitySummary? = null

    var metadata: Document? = null
}