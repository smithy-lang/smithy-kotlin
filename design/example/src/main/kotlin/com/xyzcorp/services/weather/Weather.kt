
package com.xyzcorp.services.weather

import com.xyzcorp.services.weather.model.*

interface Weather {
    suspend fun getCurrentTime(): GetCurrentTimeOutput

    suspend fun getCity(input: GetCityInput): GetCityOutput

    /**
     * @throws NoSuchResourceException
     */
    suspend fun getCityImage(input: GetCityImageInput): GetCityImageOutput

    suspend fun listCities(input: ListCitiesInput): ListCitiesOutput

    suspend fun getForecast(input: GetForecastInput): GetForecastOutput

}
