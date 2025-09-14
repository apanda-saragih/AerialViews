package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.ForecastEvent
import com.neilturner.aerialviews.services.weather.ForecastType
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.WeatherOverlay.OverlayItem.*
import com.neilturner.aerialviews.utils.FontHelper
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

class WeatherOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        var type = OverlayType.WEATHER1
        private val receiver = EventsReceiver()
        private var overlayItems: List<OverlayItem> = emptyList()
        private var overlayItemsForecast: MutableList<List<OverlayItem>> = mutableListOf()
        private var layout = ""
        private var previousWeather: WeatherEvent? = null
        private val fadeAnimationDuration = 300L

        private var font = ""
        private var size = 0f
        private var weight = ""

        sealed class OverlayItem {
            data class TextItem(
                val text: String,
            ) : OverlayItem()

            data class ImageItem(
                val imageResId: Int,
            ) : OverlayItem()
        }

        init {
            orientation = VERTICAL
            alpha = 0f
        }

        fun style(
            font: String,
            size: Float,
            weight: String,
        ) {
            this.font = font
            this.size = size
            this.weight = weight
        }

        fun layout(layout: String) {
            this.layout = layout
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            receiver.subscribe { weather: WeatherEvent ->
                Timber.d("$weather")
                updateWeather(weather)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            receiver.unsubscribe()
        }

        private fun updateWeather(weather: WeatherEvent) {
            if (layout.isEmpty()) return
            if (weather.temperature.isEmpty()) return

            // Check if the new weather data is the same as the previous data
            if (previousWeather == weather) {
                Timber.d("Weather data unchanged, skipping UI update")
                return
            }

            // If this is the first weather update, fade in directly
            if (previousWeather == null) {
                previousWeather = weather
                updateOverlayContent(weather)
                animate()
                    .alpha(1f)
                    .setDuration(fadeAnimationDuration)
                    .start()
                return
            }

            // Store current weather for next comparison
            previousWeather = weather

            // Fade out
            animate()
                .alpha(0f)
                .setDuration(fadeAnimationDuration)
                .withEndAction {
                    // Update content when fade out is complete
                    updateOverlayContent(weather)

                    // Fade back in
                    animate()
                        .alpha(1f)
                        .setDuration(fadeAnimationDuration)
                        .start()
                }.start()
        }

    private fun updateForecast(weather: WeatherEvent) {
        if (layout.isEmpty()) return
        if (weather.temperature.isEmpty()) return

        // Check if the new weather data is the same as the previous data
        if (previousWeather == weather) {
            Timber.d("Weather data unchanged, skipping UI update")
            return
        }

        // If this is the first weather update, fade in directly
        if (previousWeather == null) {
            previousWeather = weather
            updateOverlayContent(weather)
            animate()
                .alpha(1f)
                .setDuration(fadeAnimationDuration)
                .start()
            return
        }

        // Store current weather for next comparison
        previousWeather = weather

        // Fade out
        animate()
            .alpha(0f)
            .setDuration(fadeAnimationDuration)
            .withEndAction {
                // Update content when fade out is complete
                updateOverlayContent(weather)

                // Fade back in
                animate()
                    .alpha(1f)
                    .setDuration(fadeAnimationDuration)
                    .start()
            }.start()
    }

        private fun updateOverlayContent(weather: WeatherEvent) {
            overlayItems = emptyList()
            overlayItemsForecast = mutableListOf()

            Timber.i("WeatherWithForecast: $weather")

            layout.split(",").forEach { item ->
                val trimmedItem = item.trim()
                try {
                    val forecastType = ForecastType.valueOf(trimmedItem)
                    when (forecastType) {
                        ForecastType.CITY -> overlayItems = overlayItems + TextItem(weather.city)
                        ForecastType.TEMPERATURE -> overlayItems = overlayItems + TextItem(weather.temperature)
                        ForecastType.ICON -> overlayItems = overlayItems + ImageItem(weather.icon)
                        ForecastType.SUMMARY -> overlayItems = overlayItems + TextItem(weather.summary)
//                        ForecastType.HOUR -> overlayItems = overlayItems + TextItem(weather.time)
                        ForecastType.EMPTY -> { /* Do nothing */ }
                    }


//                    var second: List<OverlayItem> = emptyList()
//
//                    when (forecastType) {
//                        ForecastType.CITY -> overlayItems = overlayItems + TextItem(weather.city)
//                        ForecastType.TEMPERATURE -> overlayItems = overlayItems + TextItem(weather.temperature)
//                        ForecastType.ICON -> overlayItems = overlayItems + ImageItem(weather.icon)
//                        ForecastType.SUMMARY -> overlayItems = overlayItems + TextItem(weather.summary)
//                        ForecastType.HOUR -> overlayItems = overlayItems + TextItem(weather.time)
//                        ForecastType.EMPTY -> { /* Do nothing */ }
//                    }
//                    overlayItems = overlayItems + OverlayItem.TextItem(weather.time)

//                    for (weatherForecast in weather.forecastEvent.weatherEvents) {
//                        when (forecastType) {
//                            ForecastType.TEMPERATURE -> overlayItems =
//                                overlayItems + TextItem(weatherForecast.temperature)
//
//                            ForecastType.ICON -> overlayItems =
//                                overlayItems + ImageItem(weatherForecast.icon)
//
//                            ForecastType.SUMMARY -> overlayItems =
//                                overlayItems + TextItem(weatherForecast.summary)
//
//                            ForecastType.HOUR -> overlayItems =
//                                overlayItems + TextItem(weatherForecast.time)
//
//                            ForecastType.EMPTY -> { /* Do nothing */
//                            }
//
//                            ForecastType.CITY -> { /* Do nothing */
//                            }
//                        }
//                        overlayItems = overlayItems + OverlayItem.TextItem(weather.time)
//                    }
                } catch (e: IllegalArgumentException) {
                    Timber.e("Invalid weather info item: $trimmedItem")
                }
            }
            
            // Add the current weather items as the first row
            overlayItemsForecast.add(overlayItems)
            
            // Add forecast items as additional rows
            for (weatherForecast in weather.forecastEvent.weatherEvents) {
                var forecastItems: List<OverlayItem> = emptyList()
                
                layout.split(",").forEach { item ->
                    val trimmedItem = item.trim()
                    try {
                        val forecastType = ForecastType.valueOf(trimmedItem)
                        when (forecastType) {
                            ForecastType.CITY -> forecastItems = forecastItems + TextItem(weatherForecast.city)
                            ForecastType.TEMPERATURE -> forecastItems = forecastItems + TextItem(weatherForecast.temperature)
                            ForecastType.ICON -> forecastItems = forecastItems + ImageItem(weatherForecast.icon)
                            ForecastType.SUMMARY -> forecastItems = forecastItems + TextItem(weatherForecast.summary)
//                            ForecastType.HOUR -> forecastItems = forecastItems + TextItem(weatherForecast.time)
                            ForecastType.EMPTY -> { /* Do nothing */ }
                        }
                    } catch (e: IllegalArgumentException) {
                        Timber.e("Invalid weather info item: $trimmedItem")
                    }
                }
                
                overlayItemsForecast.add(forecastItems)
            }

            setupViews()
        }

        private fun setupViews() {
            removeAllViews()

            val iconSize = calculateIconSize(size)
            val itemMargin = 16

            Timber.d("PandaLayout: $layout")
            Timber.d("PandaWeather: $overlayItems")
            Timber.d("PandaWeatherForecast: $overlayItemsForecast")

            // Create each row (horizontal layout)
            overlayItemsForecast.forEachIndexed { rowIndex, rowItems ->
                Timber.d("Creating row $rowIndex with items: $rowItems")
                
                // Create a horizontal LinearLayout for this row
                val rowLayout = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                }

                // Add items horizontally to this row
                rowItems.forEach { item ->
                    when (item) {
                        is OverlayItem.TextItem -> {
                            val textView = TextView(context).apply {
                                text = item.text
                            }
                            TextViewCompat.setTextAppearance(textView, R.style.OverlayText)

                            // Use weight to make items proportional
                            val params = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                            params.gravity = android.view.Gravity.CENTER

                            // Add margin between items (except for the first item)
                            if (rowItems.indexOf(item) > 0) {
                                params.leftMargin = itemMargin
                            }

                            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                            textView.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                            textView.layoutParams = params

                            Timber.d("Adding text view with text: ${item.text}")
                            rowLayout.addView(textView)
                        }

                        is OverlayItem.ImageItem -> {
                            val imageView = SvgImageView(context).apply {
                                setSvgResource(item.imageResId)
                            }

                            val params = LayoutParams(iconSize, iconSize)
                            params.gravity = android.view.Gravity.CENTER_VERTICAL

                            // Add margin between items (except for the first item)
                            if (rowItems.indexOf(item) > 0) {
                                params.leftMargin = itemMargin
                            }

                            imageView.layoutParams = params
                            imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                            Timber.d("Adding image view")
                            rowLayout.addView(imageView)
                        }
                    }
                }

                // Add the row to the main vertical layout
                val rowParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                rowParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
                
                // Add margin between rows (except for the first row)
                if (overlayItemsForecast.indexOf(rowItems) > 0) {
                    rowParams.topMargin = itemMargin
                }

                rowLayout.layoutParams = rowParams
                addView(rowLayout)
                Timber.d("Added row $rowIndex to main layout")
            }
        }

        private fun calculateIconSize(size: Float): Int {
            // Get text metrics for the given size
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint

            // Calculate approximate text height based on text metrics
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }

            // Use text height directly for icon size to maintain visual balance
            val iconSize = textHeight * 1.3f
            Timber.d("Text size: ${size}sp, Text height: $textHeight, Icon size: $iconSize")
            return iconSize.toInt()
        }
    }
