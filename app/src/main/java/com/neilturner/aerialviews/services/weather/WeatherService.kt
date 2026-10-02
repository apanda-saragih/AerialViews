package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import retrofit2.Retrofit
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WeatherService(
    val context: Context,
    private val apiOverride: OpenMeteoApi? = null,
) {
    private var updateJob: Job? = null
    private val maxRetries = 3
    private var totalUpdates = 0

    private val lookupDelay = 1.seconds
    private val errorDelay = 3.seconds
    private val updateDelay = 61.minutes
    private val rateLimitDelay = 1.minutes
    private val retryDelay = 30.seconds

    private val openMeteoClient by lazy {
        apiOverride
            ?: Retrofit
                .Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(buildOkHttpClient(context))
                .addConverterFactory(buildSerializer())
                .build()
                .create(OpenMeteoApi::class.java)
    }

    suspend fun lookupLocation(query: String): List<LocationResponse> =
        try {
            val response = openMeteoClient.searchLocation(query = query, count = 10, language = "en")
            delay(lookupDelay)

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    body?.results?.map { it.toLocationResponse() } ?: emptyList()
                }

                response.code() in 500..599 -> {
                    Timber.e("Server error (${response.code()}) while fetching location data")
                    delay(errorDelay)
                    emptyList()
                }

                else -> {
                    Timber.e("Failed to fetch location data - HTTP ${response.code()}: ${response.message()}")
                    delay(errorDelay)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data")
            delay(errorDelay)
            emptyList()
        }

    suspend fun lookupLocationByCoordinates(
        lat: Double,
        lon: Double,
    ): List<LocationResponse> =
        try {
            delay(lookupDelay)
            listOf(
                LocationResponse(
                    name = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon),
                    lat = lat,
                    lon = lon,
                    country = "",
                    state = null,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data by coordinates")
            delay(errorDelay)
            emptyList()
        }

    fun startUpdates(
        fetchCurrentWeather: Boolean,
        fetchForecast: Boolean,
    ) {
        updateJob?.cancel()
        if (!fetchCurrentWeather && !fetchForecast) {
            Timber.i("Weather updates not started because no weather overlays are active")
            return
        }

        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val config = buildRequestConfig()
                    val result =
                        if (config == null) {
                            WeatherResult()
                        } else {
                            fetchWeatherData(
                                requests =
                                    WeatherRequests(
                                        fetchCurrentWeather = fetchCurrentWeather,
                                        fetchForecast = fetchForecast,
                                    ),
                                config = config,
                                displayConfig = buildDisplayConfig(),
                            )
                        }
                    if (result.weather != null) GlobalBus.post(result.weather)
                    if (result.forecast != null) GlobalBus.post(result.forecast)
                    Timber.i("Next weather update in ${updateDelay.inWholeMinutes} minutes")
                    delay(updateDelay)
                }
            }
    }

    internal fun buildRequestConfig(): WeatherRequestConfig? {
        val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
        val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
        val units = GeneralPrefs.weatherTemperatureUnits?.toString()?.lowercase() ?: "metric"

        if (lat == null || lon == null) {
            Timber.e("Invalid location coordinates")
            return null
        }

        return WeatherRequestConfig(
            apiKey = "",
            lat = lat,
            lon = lon,
            units = units,
            language = WeatherLanguage.getLanguageCode(context),
        )
    }

    internal fun buildDisplayConfig(): WeatherDisplayConfig {
        val forecastHours = GeneralPrefs.weatherForecastHours
        val locationCity =
            GeneralPrefs.weatherLocationCustomName.ifEmpty {
                GeneralPrefs.weatherLocationName.substringBefore(",").trim()
            }

        return WeatherDisplayConfig(
            currentWeatherCity = locationCity,
            forecastCity = locationCity,
            forecastDays = forecastHours,
            is24Hour = is24HourTimeFormat(),
        )
    }

    internal suspend fun fetchWeatherData(
        requests: WeatherRequests,
        config: WeatherRequestConfig,
        displayConfig: WeatherDisplayConfig,
    ): WeatherResult {
        if (!requests.fetchCurrentWeather && !requests.fetchForecast) {
            return WeatherResult()
        }

        val tempUnit = if (config.units == "imperial") "fahrenheit" else "celsius"
        val windUnit = if (config.units == "imperial") "mph" else "kmh"
        val forecastDays = if (displayConfig.forecastDays > 24) 3 else 2

        val response =
            fetchForecastResponse(
                config = config,
                tempUnit = tempUnit,
                windUnit = windUnit,
                forecastDays = forecastDays,
                attempt = 0,
            )

        var weatherEvent: WeatherEvent? = null
        var forecastEvent: ForecastEvent? = null

        if (response != null) {
            val cityName = displayConfig.currentWeatherCity

            if (requests.fetchCurrentWeather && response.current != null) {
                weatherEvent =
                    mapCurrentWeatherResponse(
                        response = response,
                        city = cityName,
                        windUnit = if (config.units == "imperial") "mph" else "km/h",
                    )
            }

            if (requests.fetchForecast && response.hourly != null) {
                forecastEvent =
                    mapForecastResponse(
                        response = response,
                        city = cityName,
                        maxHours = displayConfig.forecastDays,
                        is24Hour = displayConfig.is24Hour,
                    )
            }

            if (weatherEvent != null || forecastEvent != null) {
                totalUpdates++
            }
        }

        return WeatherResult(
            weather = weatherEvent,
            forecast = forecastEvent,
        )
    }

    private suspend fun fetchForecastResponse(
        config: WeatherRequestConfig,
        tempUnit: String,
        windUnit: String,
        forecastDays: Int,
        attempt: Int,
    ): OpenMeteoForecastResponse? =
        try {
            val response =
                openMeteoClient.getForecast(
                    lat = config.lat,
                    lon = config.lon,
                    temperatureUnit = tempUnit,
                    windSpeedUnit = windUnit,
                    forecastDays = forecastDays,
                )

            when {
                response.isSuccessful -> {
                    response.body().also { body ->
                        if (body == null) {
                            Timber.e("Received successful forecast response but body was null")
                        }
                    }
                }

                response.code() in 500..599 -> {
                    Timber.w("Forecast server error (${response.code()}) - attempt ${attempt + 1}/$maxRetries")
                    retryForecast(config, tempUnit, windUnit, forecastDays, attempt)
                }

                response.code() == 429 -> {
                    val error = "Forecast rate limit exceeded - backing off"
                    Timber.w(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    delay(rateLimitDelay)
                    null
                }

                else -> {
                    val error = "Failed to fetch forecast - HTTP ${response.code()}: ${response.message()}"
                    Timber.e(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and parse forecast data")
            FirebaseHelper.crashlyticsException(e)
            null
        }

    private suspend fun retryForecast(
        config: WeatherRequestConfig,
        tempUnit: String,
        windUnit: String,
        forecastDays: Int,
        attempt: Int,
    ): OpenMeteoForecastResponse? {
        if (attempt >= maxRetries) {
            val error = "Max retries reached for forecast server errors - giving up"
            Timber.e(error)
            FirebaseHelper.crashlyticsLogMessage(error)
            return null
        }

        delay(retryDelay)
        return fetchForecastResponse(config, tempUnit, windUnit, forecastDays, attempt + 1)
    }

    internal fun mapCurrentWeatherResponse(
        response: OpenMeteoForecastResponse,
        city: String,
        windUnit: String = "km/h",
    ): WeatherEvent {
        val current = response.current ?: return WeatherEvent()
        val isDay = current.isDay == 1
        val icon = WeatherIcons.getWmoWeatherIcon(current.weatherCode, isDay)
        val summary = WeatherIcons.getWmoWeatherDescription(current.weatherCode)
        val windSpeed = round(current.windSpeed10m)

        return WeatherEvent(
            temperature = "${current.temperature2m.roundToInt()}°",
            icon = icon,
            summary = summary,
            city = city,
            wind = "$windSpeed $windUnit",
            humidity = "${current.relativeHumidity2m.roundToInt()}%",
        )
    }

    internal fun mapForecastResponse(
        response: OpenMeteoForecastResponse,
        currentTime: ZonedDateTime =
            ZonedDateTime.now(ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofTotalSeconds(response.utcOffsetSeconds))),
        city: String,
        maxHours: Int = 24,
        is24Hour: Boolean = false,
    ): ForecastEvent {
        val hourly = response.hourly ?: return ForecastEvent(days = emptyList(), city = city)
        val forecastSlots =
            aggregateHourlyForecast(
                hourly = hourly,
                currentTime = currentTime,
                utcOffsetSeconds = response.utcOffsetSeconds,
                maxHours = maxHours,
                is24Hour = is24Hour,
            )

        Timber.i("Processed forecast: ${forecastSlots.size} slots for $city")
        return ForecastEvent(days = forecastSlots, city = city)
    }

    internal fun aggregateHourlyForecast(
        hourly: OpenMeteoHourly,
        currentTime: ZonedDateTime,
        utcOffsetSeconds: Int,
        maxHours: Int = 24,
        is24Hour: Boolean = false,
    ): List<ForecastDay> {
        val zoneOffset = java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        val currentEpoch = currentTime.toEpochSecond()
        val endEpoch = currentEpoch + (maxHours * 3600)

        val timeList = hourly.time
        val slots = mutableListOf<ForecastDay>()

        for (i in timeList.indices) {
            val dt = timeList[i]
            // Include from current hour (within 3599s before currentEpoch) up to endEpoch
            if (dt in (currentEpoch - 3599)..endEpoch) {
                val isFirstAndCurrent = slots.isEmpty() && (currentEpoch - dt in 0..3599 || kotlin.math.abs(dt - currentEpoch) <= 1800)
                val timeLabel =
                    if (isFirstAndCurrent) {
                        "Now"
                    } else {
                        val itemTime = Instant.ofEpochSecond(dt).atOffset(zoneOffset)
                        itemTime.format(DateTimeFormatter.ofPattern("HH", Locale.getDefault()))
                    }

                val temp =
                    hourly.temperature2m
                        .getOrNull(i)
                        ?.roundToInt()
                        ?.let { "$it°" } ?: ""
                val rawPop = hourly.precipitationProbability.getOrNull(i) ?: 0
                val rainAmount = hourly.precipitation.getOrNull(i) ?: 0.0
                val code = hourly.weatherCode.getOrNull(i) ?: 0
                val isDay = (hourly.isDay.getOrNull(i) ?: 1) == 1
                val icon = WeatherIcons.getWmoWeatherIcon(code, isDay)
                val isPrecipitation = WeatherIcons.isPrecipitationWeatherCode(code)

                val pop =
                    when {
                        !isPrecipitation && rainAmount < 0.1 -> 0
                        rainAmount == 0.0 -> minOf(rawPop, 20)
                        rainAmount < 0.2 -> minOf(rawPop, 35)
                        rainAmount < 0.5 -> minOf(rawPop, 60)
                        else -> rawPop
                    }.coerceIn(0, 100)

                slots.add(
                    ForecastDay(
                        dayName = timeLabel,
                        icon = icon,
                        tempHigh = temp,
                        tempLow = "",
                        pop = pop,
                    ),
                )

                if (slots.size >= maxHours) {
                    break
                }
            }
        }

        return slots
    }

    private fun is24HourTimeFormat(): Boolean =
        try {
            when (GeneralPrefs.clockFormat) {
                ClockType.HOUR_24 -> {
                    true
                }

                ClockType.HOUR_12 -> {
                    false
                }

                else -> {
                    android.text.format.DateFormat
                        .is24HourFormat(context)
                }
            }
        } catch (t: Throwable) {
            false
        }

    fun getWeatherIcon(
        code: Int,
        type: String,
        icon: String,
    ): Int = WeatherIcons.getWeatherIcon(code, type, icon)

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        FirebaseHelper.analyticsEvent(
            "weather_updates",
            Bundle().apply {
                putDouble("value", totalUpdates.toDouble())
            },
        )
        Timber.i("Weather updates stopped, total updates for session: $totalUpdates")
    }
}

data class WeatherEvent(
    val temperature: String = "",
    val icon: Int = -1,
    val summary: String = "",
    val city: String = "",
    val wind: String = "",
    val humidity: String = "",
)

data class ForecastDay(
    val dayName: String = "",
    val icon: Int = -1,
    val tempHigh: String = "",
    val tempLow: String = "",
    val pop: Int = 0,
)

data class ForecastEvent(
    val days: List<ForecastDay> = emptyList(),
    val city: String = "",
)

data class WeatherResult(
    val weather: WeatherEvent? = null,
    val forecast: ForecastEvent? = null,
)

data class WeatherRequests(
    val fetchCurrentWeather: Boolean = false,
    val fetchForecast: Boolean = false,
)

data class WeatherRequestConfig(
    val apiKey: String = "",
    val lat: Double,
    val lon: Double,
    val units: String,
    val language: String,
)

data class WeatherDisplayConfig(
    val currentWeatherCity: String = "",
    val forecastCity: String = "",
    val forecastDays: Int = 24,
    val is24Hour: Boolean = false,
)
