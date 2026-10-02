package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.R
import timber.log.Timber

/**
 * Helper class for mapping OpenWeather condition codes to drawable resources
 * OpenWeather condition codes reference: https://openweathermap.org/weather-conditions
 */
object WeatherIcons {
    // Weather condition code groups (first digit of the code)
    private const val GROUP_THUNDERSTORM = 2 // 200-299
    private const val GROUP_DRIZZLE = 3 // 300-399
    private const val GROUP_RAIN = 5 // 500-599
    private const val GROUP_SNOW = 6 // 600-699
    private const val GROUP_ATMOSPHERE = 7 // 700-799
    private const val GROUP_CLEAR = 800 // 800
    private const val GROUP_CLOUDS = 8 // 801-899

    // Time of day indicators from OpenWeather icon codes
    private const val ICON_DAY = 'd'
    private const val ICON_NIGHT = 'n'

    /**
     * Maps OpenWeather condition codes to appropriate drawable resources
     *
     * @param conditionCode The weather condition code from OpenWeather API
     * @param conditionType The main weather type (e.g., "Rain", "Snow", "Clear")
     * @param iconCode The icon code from OpenWeather (e.g., "01d", "02n")
     * @return The resource ID for the appropriate weather icon
     */
    fun getWeatherIcon(
        conditionCode: Int,
        conditionType: String,
        iconCode: String,
    ): Int {
        Timber.d("Weather condition: code=$conditionCode, type=$conditionType, icon=$iconCode")

        // Default fallback icon
        var iconResource = R.drawable.weather_clear
        val isNight = iconCode.lastOrNull() == ICON_NIGHT

        // Map the condition code to the appropriate icon
        when {
            // Thunderstorm group (200-299)
            conditionCode / 100 == GROUP_THUNDERSTORM -> {
                iconResource =
                    if (isNight) {
                        R.drawable.weather_thunder
                    } else {
                        R.drawable.weather_thunder
                    }
            }

            // Drizzle group (300-399)
            conditionCode / 100 == GROUP_DRIZZLE -> {
                iconResource =
                    if (isNight) {
                        R.drawable.weather_rain_heavy
                    } else {
                        R.drawable.weather_rain_heavy
                    }
            }

            // Rain group (500-599)
            conditionCode / 100 == GROUP_RAIN -> {
                // Handle different types of rain
                iconResource =
                    when (conditionCode) {
                        in 500..501 -> { // Light
                            if (isNight) R.drawable.weather_rain_light else R.drawable.weather_rain_light
                        }

                        in 502..504 -> { // Heavy rain
                            if (isNight) R.drawable.weather_rain_heavy else R.drawable.weather_rain_heavy
                        }

                        511 -> { // Freezing rain
                            if (isNight) R.drawable.weather_rain_heavy else R.drawable.weather_rain_heavy
                        }

                        521 -> { // Shower rain
                            if (isNight) R.drawable.weather_rain_light else R.drawable.weather_rain_light
                        }

                        else -> { // Shower heavy, etc
                            if (isNight) R.drawable.weather_rain_heavy else R.drawable.weather_rain_heavy
                        }
                    }
            }

            // Snow group (600-699)
            conditionCode / 100 == GROUP_SNOW -> {
                iconResource =
                    if (isNight) {
                        R.drawable.weather_snow
                    } else {
                        R.drawable.weather_snow
                    }
            }

            // Atmosphere group (700-799): fog, mist, etc.
            conditionCode / 100 == GROUP_ATMOSPHERE -> {
                iconResource =
                    if (isNight) {
                        R.drawable.weather_mist
                    } else {
                        R.drawable.weather_mist
                    }
            }

            // Clear sky (800)
            conditionCode == GROUP_CLEAR -> {
                iconResource =
                    if (isNight) {
                        R.drawable.weather_clear_night
                    } else {
                        R.drawable.weather_clear
                    }
            }

            // Clouds group (801-899)
            conditionCode / 100 == GROUP_CLOUDS -> {
                // Different levels of cloudiness
                iconResource =
                    when (conditionCode) {
                        801 -> { // Few clouds
                            if (isNight) R.drawable.weather_cloudy_light else R.drawable.weather_cloudy_light
                        }

                        802 -> { // Scattered clouds
                            if (isNight) R.drawable.weather_cloudy_light else R.drawable.weather_cloudy_light
                        }

                        else -> { // Broken or overcast clouds
                            if (isNight) R.drawable.weather_cloudy_light else R.drawable.weather_cloudy_light
                        }
                    }
            }
        }

        return iconResource
    }

    /**
     * Maps WMO weather interpretation codes to appropriate drawable resources
     * Reference: https://open-meteo.com/en/docs
     */
    fun getWmoWeatherIcon(
        weatherCode: Int,
        isDay: Boolean,
    ): Int =
        when (weatherCode) {
            0 -> if (isDay) R.drawable.weather_clear else R.drawable.weather_clear_night
            1, 2, 3 -> R.drawable.weather_cloudy_light
            45, 48 -> R.drawable.weather_mist
            51, 53, 61, 63, 80, 81 -> R.drawable.weather_rain_light
            55, 56, 57, 65, 66, 67, 82 -> R.drawable.weather_rain_heavy
            71, 73, 75, 77, 85, 86 -> R.drawable.weather_snow
            95, 96, 99 -> R.drawable.weather_thunder
            else -> if (isDay) R.drawable.weather_clear else R.drawable.weather_clear_night
        }

    /**
     * Maps WMO weather interpretation codes to human-readable description
     */
    fun getWmoWeatherDescription(weatherCode: Int): String =
        when (weatherCode) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Depositing rime fog"
            51 -> "Light drizzle"
            53 -> "Moderate drizzle"
            55 -> "Dense drizzle"
            56 -> "Light freezing drizzle"
            57 -> "Dense freezing drizzle"
            61 -> "Slight rain"
            63 -> "Moderate rain"
            65 -> "Heavy rain"
            66 -> "Light freezing rain"
            67 -> "Heavy freezing rain"
            71 -> "Slight snow fall"
            73 -> "Moderate snow fall"
            75 -> "Heavy snow fall"
            77 -> "Snow grains"
            80 -> "Slight rain showers"
            81 -> "Moderate rain showers"
            82 -> "Violent rain showers"
            85 -> "Slight snow showers"
            86 -> "Heavy snow showers"
            95 -> "Thunderstorm"
            96 -> "Thunderstorm with slight hail"
            99 -> "Thunderstorm with heavy hail"
            else -> "Clear"
        }

    /**
     * Checks if a WMO weather code represents an active precipitation condition
     * (drizzle, rain, snow, showers, thunderstorm)
     */
    fun isPrecipitationWeatherCode(weatherCode: Int): Boolean =
        when (weatherCode) {
            in 51..57, // Drizzle & Freezing drizzle
            in 61..67, // Rain & Freezing rain
            in 71..77, // Snow
            in 80..82, // Rain showers
            in 85..86, // Snow showers
            in 95..99, // Thunderstorm
            -> true

            else -> false
        }
}
