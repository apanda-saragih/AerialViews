package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("daily") daily: String,
        @Query("current") current: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("forecast_hours") forecastHours: Int,
        @Query("timezone") timezone: String,
    ): Response<OpenMeteoResponse>
}

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: Current,
    val hourly: Hourly,
    val daily: Daily
)

@Serializable
data class Current(
    val time: String,
    val interval: Int,
    @SerialName("temperature_2m")
    val temperature2m: Double,
    @SerialName("weather_code")
    val weatherCode: Int
)

@Serializable
data class Hourly(
    val time: List<String>,
    @SerialName("temperature_2m")
    val temperature2m: List<Double>,
    @SerialName("weather_code")
    val weatherCode: List<Int>,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: List<Int>,
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Int>
)

@Serializable
data class Daily(
    val time: List<String>,
    @SerialName("weather_code")
    val weatherCode: List<Int>,
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double>,
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double>
)
