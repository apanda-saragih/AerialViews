package com.neilturner.aerialviews.services.weather

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@DisplayName("Weather Service Tests")
internal class WeatherServiceTest {
    private val context: Context = mockk(relaxed = true)

    @Test
    @DisplayName("Should map current weather response to weather event")
    fun shouldMapCurrentWeatherResponse() {
        val service = WeatherService(context, apiOverride = FakeOpenWeatherApi())
        val response =
            CurrentWeatherResponse(
                weather = listOf(Weather(id = 800, main = "Clear", description = "clear sky", icon = "01d")),
                main =
                    MainWeatherData(
                        temp = 12.6,
                        feelsLike = 11.2,
                        tempMin = 10.0,
                        tempMax = 14.0,
                        pressure = 1012,
                        humidity = 77,
                    ),
                wind = Wind(speed = 9.8, deg = 180),
                name = "Dublin",
            )

        val event = service.mapCurrentWeatherResponse(response, city = "Custom City")

        assertEquals("13°", event.temperature)
        assertEquals("Clear sky", event.summary)
        assertEquals("Custom City", event.city)
        assertEquals("10.0 km/h", event.wind)
        assertEquals("77%", event.humidity)
    }

    @Test
    @DisplayName("Should aggregate forecast response into 24 hour slots")
    fun shouldAggregateForecastResponse() {
        val service = WeatherService(context, apiOverride = FakeOpenWeatherApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val response =
            FiveDayForecastResponse(
                list =
                    listOf(
                        forecastItem("2026-04-03T12:00:00", 15.0, 9.0, "01d"),
                        forecastItem("2026-04-03T15:00:00", 18.0, 11.0, "02d"),
                        forecastItem("2026-04-03T18:00:00", 16.0, 10.0, "02d"),
                        forecastItem("2026-04-04T00:00:00", 12.0, 8.0, "10d"),
                        forecastItem("2026-04-04T12:00:00", 17.0, 11.0, "01d"),
                        forecastItem("2026-04-05T12:00:00", 9.0, 4.0, "13d"), // 48h later, should be excluded
                    ),
                city = City(name = "Dublin", country = "IE", timezone = 0),
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
        assertEquals(5, event.days.size)
        assertEquals("Now", event.days[0].dayName)
        assertEquals("15°", event.days[0].tempHigh)
        assertEquals("", event.days[0].tempLow)
        assertEquals("15", event.days[1].dayName)
        assertEquals("18°", event.days[1].tempHigh)
    }

    @Test
    @DisplayName("Should format hours as 24-hour when requested")
    fun shouldFormatHoursAs24Hour() {
        val service = WeatherService(context, apiOverride = FakeOpenWeatherApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val response =
            FiveDayForecastResponse(
                list =
                    listOf(
                        forecastItem("2026-04-03T12:00:00", 15.0, 9.0, "01d"),
                        forecastItem("2026-04-03T15:00:00", 18.0, 11.0, "02d"),
                        forecastItem("2026-04-03T23:00:00", 11.0, 8.0, "02d"),
                    ),
                city = City(name = "Dublin", country = "IE", timezone = 0),
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
        assertEquals("15", event.days[1].dayName)
        assertEquals("23", event.days[2].dayName)
    }

    @Test
    @DisplayName("Should call only current weather endpoint when current weather requested")
    fun shouldFetchCurrentWeatherOnly() =
        runTest {
            val api = FakeOpenWeatherApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.currentWeatherCalls)
            assertEquals(0, api.forecastCalls)
            assertEquals("13°", result.weather?.temperature)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should call only forecast endpoint when forecast requested")
    fun shouldFetchForecastOnly() =
        runTest {
            val api = FakeOpenWeatherApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = true),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(0, api.currentWeatherCalls)
            assertEquals(1, api.forecastCalls)
            assertNull(result.weather)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should call both endpoints when both weather overlays are requested")
    fun shouldFetchCurrentWeatherAndForecast() =
        runTest {
            val api = FakeOpenWeatherApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = true),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.currentWeatherCalls)
            assertEquals(1, api.forecastCalls)
            assertEquals("13°", result.weather?.temperature)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should skip all API calls when no weather overlays are requested")
    fun shouldSkipApiCallsWhenNoWeatherRequests() =
        runTest {
            val api = FakeOpenWeatherApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(0, api.currentWeatherCalls)
            assertEquals(0, api.forecastCalls)
            assertNull(result.weather)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should parse probability of precipitation into percentage")
    fun shouldParseProbabilityOfPrecipitation() {
        val service = WeatherService(context, apiOverride = FakeOpenWeatherApi())
        val currentZoned = ZonedDateTime.of(2026, 4, 3, 12, 0, 0, 0, ZoneOffset.UTC)
        val response =
            FiveDayForecastResponse(
                list =
                    listOf(
                        forecastItem("2026-04-03T12:00:00", 15.0, 9.0, "01d", pop = 0.0),
                        forecastItem("2026-04-03T15:00:00", 18.0, 11.0, "10d", pop = 0.65),
                        forecastItem("2026-04-03T18:00:00", 16.0, 10.0, "10d", pop = 0.20),
                        forecastItem("2026-04-03T21:00:00", 14.0, 9.0, "02d", pop = null),
                    ),
                city = City(name = "Dublin", country = "IE", timezone = 0),
            )

        val event =
            service.mapForecastResponse(
                response = response,
                currentTime = currentZoned,
                city = "Dublin",
                maxHours = 24,
                is24Hour = false,
            )

        assertEquals(4, event.days.size)
        assertEquals(0, event.days[0].pop)
        assertEquals(65, event.days[1].pop)
        assertEquals(20, event.days[2].pop)
        assertEquals(0, event.days[3].pop)
    }

    private fun forecastItem(
        dateTime: String,
        tempMax: Double,
        tempMin: Double,
        icon: String,
        pop: Double? = null,
    ): ForecastItem {
        val instant = LocalDateTime.parse(dateTime).toEpochSecond(ZoneOffset.UTC)
        return ForecastItem(
            dt = instant,
            main =
                MainWeatherData(
                    temp = tempMax,
                    feelsLike = tempMax,
                    tempMin = tempMin,
                    tempMax = tempMax,
                    pressure = 1000,
                    humidity = 70,
                ),
            weather = listOf(Weather(id = 800, main = "Clear", description = "clear sky", icon = icon)),
            wind = Wind(speed = 5.4, deg = 90),
            dtTxt = dateTime.replace('T', ' '),
            pop = pop,
        )
    }

    private fun requestConfig() =
        WeatherRequestConfig(
            apiKey = "key",
            lat = 53.3498,
            lon = -6.2603,
            units = "metric",
            language = "en",
        )

    private fun displayConfig() =
        WeatherDisplayConfig(
            currentWeatherCity = "Custom City",
            forecastCity = "Custom City",
            forecastDays = 3,
        )
}

private class FakeOpenWeatherApi : OpenWeatherApi {
    var currentWeatherCalls = 0
    var forecastCalls = 0

    override suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        apiKey: String,
        units: String,
        language: String,
    ): Response<CurrentWeatherResponse> {
        currentWeatherCalls++
        return Response.success(
            CurrentWeatherResponse(
                weather = listOf(Weather(id = 800, main = "Clear", description = "clear sky", icon = "01d")),
                main =
                    MainWeatherData(
                        temp = 12.6,
                        feelsLike = 11.0,
                        tempMin = 10.0,
                        tempMax = 14.0,
                        pressure = 1009,
                        humidity = 75,
                    ),
                wind = Wind(speed = 9.5, deg = 180),
                name = "Dublin",
            ),
        )
    }

    override suspend fun getForecast(
        lat: Double,
        lon: Double,
        apiKey: String,
        units: String,
        language: String,
    ): Response<FiveDayForecastResponse> {
        forecastCalls++
        val now = LocalDateTime.now(ZoneOffset.UTC)
        return Response.success(
            FiveDayForecastResponse(
                list =
                    listOf(
                        fakeForecastItem(now.toString(), 15.0, 9.0),
                        fakeForecastItem(now.plusHours(3).toString(), 17.0, 10.0),
                        fakeForecastItem(now.plusHours(6).toString(), 14.0, 8.0),
                    ),
                city = City(name = "Dublin", country = "IE", timezone = 0),
            ),
        )
    }

    override suspend fun getLocationByName(
        locationName: String,
        limit: Int,
        apiKey: String,
        language: String,
    ): Response<List<LocationResponse>> = Response.success(emptyList())

    override suspend fun getLocationByCoordinates(
        lat: Double,
        lon: Double,
        limit: Int,
        apiKey: String,
        language: String,
    ): Response<List<LocationResponse>> = Response.success(emptyList())

    private fun fakeForecastItem(
        dateTime: String,
        tempMax: Double,
        tempMin: Double,
    ): ForecastItem {
        val instant = LocalDateTime.parse(dateTime).toEpochSecond(ZoneOffset.UTC)
        return ForecastItem(
            dt = instant,
            main =
                MainWeatherData(
                    temp = tempMax,
                    feelsLike = tempMax,
                    tempMin = tempMin,
                    tempMax = tempMax,
                    pressure = 1000,
                    humidity = 70,
                ),
            weather = listOf(Weather(id = 800, main = "Clear", description = "clear sky", icon = "01d")),
            wind = Wind(speed = 5.0, deg = 90),
            dtTxt = dateTime.replace('T', ' '),
        )
    }
}
