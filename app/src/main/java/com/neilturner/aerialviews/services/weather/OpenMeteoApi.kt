package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation,weather_code,is_day",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeformat: String = "unixtime",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("forecast_days") forecastDays: Int = 3,
    ): Response<OpenMeteoForecastResponse>

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchLocation(
        @Query("name") query: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): Response<OpenMeteoGeocodingResponse>
}

@Serializable
data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String = "UTC",
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
)

@Serializable
data class OpenMeteoCurrent(
    val time: Long,
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed10m: Double = 0.0,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
data class OpenMeteoHourly(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("precipitation") val precipitation: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("is_day") val isDay: List<Int> = emptyList(),
)

@Serializable
data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoLocationItem>? = null,
)

@Serializable
data class OpenMeteoLocationItem(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String? = null,
) {
    fun toLocationResponse(): LocationResponse =
        LocationResponse(
            name = name,
            lat = latitude,
            lon = longitude,
            country = country,
            state = admin1,
        )
}
