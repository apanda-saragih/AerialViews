package com.neilturner.aerialviews.services.weather

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar

@DisplayName("Weather Service Tests")
internal class WeatherServiceTest {
    private val context: Context = mockk(relaxed = true)

    @Test
    @DisplayName("Should map current weather response to weather event")
    fun shouldMapCurrentWeatherResponse() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val response =
            OpenMeteoForecastResponse(
                latitude = 53.3498,
                longitude = -6.2603,
                utcOffsetSeconds = 0,
                current =
                    OpenMeteoCurrent(
                        time = 1712145600,
                        temperature2m = 12.6,
                        relativeHumidity2m = 77.0,
                        weatherCode = 0,
                        windSpeed10m = 9.8,
                        isDay = 1,
                    ),
            )

        val event = service.mapCurrentWeatherResponse(response, city = "Custom City")

        assertEquals("13°", event.temperature)
        assertEquals("Clear sky", event.summary)
        assertEquals("Custom City", event.city)
        assertEquals("10.0 km/h", event.wind)
        assertEquals("77%", event.humidity)
    }

    @Test
    @DisplayName("Should aggregate forecast response into hourly slots")
    fun shouldAggregateForecastResponse() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val currentEpoch = currentZoned.toEpochSecond()

        val times = (0..26).map { currentEpoch + (it * 3600) }
        val temps = (0..26).map { 15.0 + (it % 5) }
        val pops = (0..26).map { 10 * (it % 10) }
        val codes = (0..26).map { 1 }
        val isDays = (0..26).map { 1 }

        val response =
            OpenMeteoForecastResponse(
                latitude = 53.3498,
                longitude = -6.2603,
                utcOffsetSeconds = 0,
                hourly =
                    OpenMeteoHourly(
                        time = times,
                        temperature2m = temps,
                        precipitationProbability = pops,
                        weatherCode = codes,
                        isDay = isDays,
                    ),
            )

        val event =
            service.mapForecastResponse(
                response = response,
                currentTime = currentZoned,
                city = "Custom City",
                maxHours = 24,
                is24Hour = false,
            )

        assertEquals("Custom City", event.city)
        assertEquals(24, event.days.size)
        assertEquals("Now", event.days[0].dayName)
        assertEquals("15°", event.days[0].tempHigh)
        assertEquals("", event.days[0].tempLow)
        assertEquals("13", event.days[1].dayName)
        assertEquals("16°", event.days[1].tempHigh)
        assertEquals("14", event.days[2].dayName)
    }

    @Test
    @DisplayName("Should format hours as 24-hour when requested")
    fun shouldFormatHoursAs24Hour() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val currentEpoch = currentZoned.toEpochSecond()

        val response =
            OpenMeteoForecastResponse(
                latitude = 53.3498,
                longitude = -6.2603,
                utcOffsetSeconds = 0,
                hourly =
                    OpenMeteoHourly(
                        time = listOf(currentEpoch, currentEpoch + 3600, currentEpoch + 7200),
                        temperature2m = listOf(15.0, 18.0, 11.0),
                        precipitationProbability = listOf(0, 20, 50),
                        weatherCode = listOf(0, 1, 2),
                        isDay = listOf(1, 1, 1),
                    ),
            )

        val event =
            service.mapForecastResponse(
                response = response,
                currentTime = currentZoned,
                city = "Custom City",
                maxHours = 24,
                is24Hour = true,
            )

        assertEquals(3, event.days.size)
        assertEquals("Now", event.days[0].dayName)
        assertEquals("13", event.days[1].dayName)
        assertEquals("14", event.days[2].dayName)
    }

    @Test
    @DisplayName("Should call forecast endpoint and populate current weather only")
    fun shouldFetchCurrentWeatherOnly() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertEquals("13°", result.weather?.temperature)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should call forecast endpoint and populate forecast only")
    fun shouldFetchForecastOnly() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = true),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertNull(result.weather)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should call forecast endpoint and populate both current and forecast")
    fun shouldFetchCurrentWeatherAndForecast() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = true),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertEquals("13°", result.weather?.temperature)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should skip all API calls when no weather overlays are requested")
    fun shouldSkipApiCallsWhenNoWeatherRequests() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(0, api.forecastCalls)
            assertNull(result.weather)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should parse probability of precipitation into calibrated percentage")
    fun shouldParseProbabilityOfPrecipitation() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val currentEpoch = currentZoned.toEpochSecond()

        val response =
            OpenMeteoForecastResponse(
                latitude = 53.3498,
                longitude = -6.2603,
                utcOffsetSeconds = 0,
                hourly =
                    OpenMeteoHourly(
                        time =
                            listOf(
                                currentEpoch,
                                currentEpoch + 3600,
                                currentEpoch + 7200,
                                currentEpoch + 10800,
                                currentEpoch + 14400,
                                currentEpoch + 18000,
                            ),
                        temperature2m = listOf(15.0, 18.0, 16.0, 14.0, 13.0, 12.0),
                        precipitationProbability = listOf(0, 65, 20, null, 94, 90),
                        precipitation = listOf(0.0, 1.2, 0.1, 0.0, 0.0, 0.1),
                        weatherCode = listOf(0, 61, 61, 2, 3, 51),
                        isDay = listOf(1, 1, 1, 1, 1, 1),
                    ),
            )

        val event =
            service.mapForecastResponse(
                response = response,
                currentTime = currentZoned,
                city = "Dublin",
                maxHours = 24,
                is24Hour = false,
            )

        assertEquals(6, event.days.size)
        // Clear sky (code 0) with 0.0mm -> 0%
        assertEquals(0, event.days[0].pop)
        // Rain (code 61) with 1.2mm (> 0.5mm) -> raw 65%
        assertEquals(65, event.days[1].pop)
        // Rain (code 61) with 0.1mm (< 0.2mm) -> min(20, 35) = 20%
        assertEquals(20, event.days[2].pop)
        // Partly cloudy (code 2) with 0.0mm -> 0%
        assertEquals(0, event.days[3].pop)
        // Overcast (code 3) with 0.0mm -> suppressed from raw 94% to 0%
        assertEquals(0, event.days[4].pop)
        // Light drizzle (code 51) with 0.1mm trace -> scaled from raw 90% to 35%
        assertEquals(35, event.days[5].pop)
    }

    @Test
    @DisplayName("Should lookup location by name without requiring API key")
    fun shouldLookupLocationByName() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val results = service.lookupLocation("Jakarta")

            assertEquals(1, api.locationCalls)
            assertEquals(1, results.size)
            assertEquals("Jakarta", results[0].name)
            assertEquals(-6.2146, results[0].lat, 0.001)
            assertEquals(106.8451, results[0].lon, 0.001)
            assertEquals("Indonesia", results[0].country)
        }

    @Test
    @DisplayName("Should calculate delay until next hour with buffer")
    fun shouldCalculateDelayUntilNextHour() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())

        // 1. Exactly at 20:45:00.000 (15 minutes remaining until next hour)
        val cal45 =
            Calendar.getInstance().apply {
                set(Calendar.MINUTE, 45)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val delay45 = service.calculateDelayUntilNextHour(calendar = cal45, bufferMillis = 5_000L)
        // 15 min (900,000 ms) + 5s (5,000 ms) = 905,000 ms
        assertEquals(905_000L, delay45)

        // 2. 10 seconds before next hour (20:59:50.000)
        val cal59 =
            Calendar.getInstance().apply {
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 50)
                set(Calendar.MILLISECOND, 0)
            }
        val delay59 = service.calculateDelayUntilNextHour(calendar = cal59, bufferMillis = 5_000L)
        // 10s (10,000 ms) + 5s (5,000 ms) = 15,000 ms
        assertEquals(15_000L, delay59)

        // 3. Just after top of the hour (21:00:05.000)
        val cal00 =
            Calendar.getInstance().apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
        val delay00 = service.calculateDelayUntilNextHour(calendar = cal00, bufferMillis = 5_000L)
        // (3600 - 5)s (3,595,000 ms) + 5s (5,000 ms) = 3,600,000 ms (exactly 1 hour)
        assertEquals(3_600_000L, delay00)
    }

    private fun requestConfig() =
        WeatherRequestConfig(
            apiKey = "",
            lat = 53.3498,
            lon = -6.2603,
            units = "metric",
            language = "en",
        )

    private fun displayConfig() =
        WeatherDisplayConfig(
            currentWeatherCity = "Custom City",
            forecastCity = "Custom City",
            forecastDays = 24,
        )
}

private class FakeOpenMeteoApi : OpenMeteoApi {
    var forecastCalls = 0
    var locationCalls = 0

    override suspend fun getForecast(
        lat: Double,
        lon: Double,
        current: String,
        hourly: String,
        timezone: String,
        timeformat: String,
        temperatureUnit: String,
        windSpeedUnit: String,
        forecastDays: Int,
    ): Response<OpenMeteoForecastResponse> {
        forecastCalls++
        val now = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC)
        return Response.success(
            OpenMeteoForecastResponse(
                latitude = lat,
                longitude = lon,
                utcOffsetSeconds = 0,
                current =
                    OpenMeteoCurrent(
                        time = now,
                        temperature2m = 12.6,
                        relativeHumidity2m = 75.0,
                        weatherCode = 0,
                        windSpeed10m = 9.5,
                        isDay = 1,
                    ),
                hourly =
                    OpenMeteoHourly(
                        time = listOf(now, now + 3600, now + 7200),
                        temperature2m = listOf(15.0, 17.0, 14.0),
                        precipitationProbability = listOf(0, 20, 40),
                        weatherCode = listOf(0, 1, 2),
                        isDay = listOf(1, 1, 1),
                    ),
            ),
        )
    }

    override suspend fun searchLocation(
        query: String,
        count: Int,
        language: String,
        format: String,
    ): Response<OpenMeteoGeocodingResponse> {
        locationCalls++
        return Response.success(
            OpenMeteoGeocodingResponse(
                results =
                    listOf(
                        OpenMeteoLocationItem(
                            id = 1642911,
                            name = "Jakarta",
                            latitude = -6.2146,
                            longitude = 106.8451,
                            country = "Indonesia",
                            admin1 = "Jakarta",
                        ),
                    ),
            ),
        )
    }
}
