package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.ForecastDay
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState
import timber.log.Timber

class WeatherForecastOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : HorizontalScrollView(context, attrs, defStyleAttr) {
        var type = OverlayType.WEATHER2
        private var previousDays: List<ForecastDay>? = null
        var isHidden = false
        var maxWidth: Int = 0
            set(value) {
                field = value
                requestLayout()
            }

        private val contentLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }

        // Layout constants
        private val fadeAnimationDuration = 300L
        private val minVisibleAlphaForFade = 0.95f
        private val cellSpacing = 20 // dp between hourly columns
        private val elementMargin = 4 // dp between elements
        private val iconScale = 1.1f // multiplier relative to text height

        // Text size multipliers (relative to base size)
        private val dayLabelSizeRatio = 0.8f
        private val tempSizeRatio = 0.8f
        private val popSizeRatio = 0.65f

        // Text alpha (0-255)
        private val dayLabelAlpha = 200
        private val highTempAlpha = 230
        private val lowTempAlpha = 140
        private val popAlpha = 230

        private var font = ""
        private var size = 0f
        private var weight = ""

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = false
            addView(contentLayout)
            alpha = 1f
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Prevent parent from intercepting horizontal scroll gestures on touch
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onTouchEvent(ev)
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val constrainedWidthSpec =
                if (maxWidth > 0) {
                    val constrainedSize =
                        if (widthMode == MeasureSpec.UNSPECIFIED) {
                            maxWidth
                        } else {
                            minOf(widthSize, maxWidth)
                        }
                    MeasureSpec.makeMeasureSpec(constrainedSize, MeasureSpec.AT_MOST)
                } else {
                    widthMeasureSpec
                }
            super.onMeasure(constrainedWidthSpec, heightMeasureSpec)
            if (maxWidth > 0 && measuredWidth > maxWidth) {
                setMeasuredDimension(maxWidth, measuredHeight)
            }
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

        fun render(state: ForecastOverlayState) {
            val days = state.event.days
            if (days.isEmpty()) {
                Timber.d("Forecast render: no forecast data yet")
                return
            }
            Timber.d("Forecast render: ${days.size} slots")

            if (previousDays == days) {
                Timber.d("Forecast data unchanged, skipping UI update")
                return
            }

            val allowFadeAnimation = alpha >= minVisibleAlphaForFade

            if (previousDays == null) {
                previousDays = days
                updateForecastContent(days)
                if (allowFadeAnimation && !isHidden) {
                    animate().alpha(1f).setDuration(fadeAnimationDuration).start()
                }
                return
            }

            previousDays = days

            if (!allowFadeAnimation || isHidden) {
                updateForecastContent(days)
                return
            }

            animate()
                .alpha(0f)
                .setDuration(fadeAnimationDuration)
                .withEndAction {
                    updateForecastContent(days)
                    if (!isHidden) {
                        animate().alpha(1f).setDuration(fadeAnimationDuration).start()
                    }
                }.start()
        }

        private fun updateForecastContent(days: List<ForecastDay>) {
            contentLayout.removeAllViews()
            scrollTo(0, 0)

            val effectiveSize = if (size > 0f) size else 18f
            val iconSize = calculateIconSize(effectiveSize)
            val density = resources.displayMetrics.density
            val spacingPx = (cellSpacing * density).toInt()
            val hasPrecipitation = days.any { it.pop >= 10 }

            days.forEachIndexed { index, day ->
                val column = createSlotColumn(day, effectiveSize, iconSize, hasPrecipitation)
                contentLayout.addView(column)

                if (index < days.size - 1) {
                    val spacer =
                        View(context).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    spacingPx,
                                    0,
                                )
                        }
                    contentLayout.addView(spacer)
                }
            }

            Timber.d("Forecast content updated: ${days.size} columns, iconSize=$iconSize")
        }

        private fun createSlotColumn(
            day: ForecastDay,
            effectiveSize: Float,
            iconSize: Int,
            hasPrecipitation: Boolean,
        ): LinearLayout {
            val column =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                }

            val density = resources.displayMetrics.density
            val elementMarginPx = (elementMargin * density).toInt()

            // 1. Time label on top (e.g. "Now", "20", "23")
            val dayLabel =
                TextView(context).apply {
                    text = day.dayName
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }
            TextViewCompat.setTextAppearance(dayLabel, R.style.OverlayText)
            dayLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize * dayLabelSizeRatio)
            dayLabel.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
            val dayLabelOffset = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, dayLabel.textSize)
            dayLabel.setPadding(0, dayLabelOffset, 0, -dayLabelOffset)
            dayLabel.setTextColor(Color.argb(dayLabelAlpha, 255, 255, 255))
            val labelParams =
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        bottomMargin = elementMarginPx
                    }
            dayLabel.layoutParams = labelParams
            column.addView(dayLabel)

            // 2. Weather icon
            if (day.icon > 0) {
                val iconView =
                    SvgImageView(context).apply {
                        setSvgResource(day.icon)
                    }
                val iconParams =
                    LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        if (!hasPrecipitation) {
                            bottomMargin = elementMarginPx
                        }
                    }
                iconView.layoutParams = iconParams
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                column.addView(iconView)
            } else {
                val placeholder =
                    View(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(iconSize, iconSize).apply {
                                if (!hasPrecipitation) {
                                    bottomMargin = elementMarginPx
                                }
                            }
                    }
                column.addView(placeholder)
            }

            // 3. Rain percentage right below the icon (if precipitation exists in timeline)
            if (hasPrecipitation) {
                val popText = if (day.pop >= 10) "${day.pop}%" else ""
                val popLabel =
                    TextView(context).apply {
                        text = popText
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    }
                TextViewCompat.setTextAppearance(popLabel, R.style.OverlayText)
                popLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize * popSizeRatio)
                popLabel.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                val popOffset = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, popLabel.textSize)
                popLabel.setPadding(0, popOffset, 0, -popOffset)
                popLabel.setTextColor(Color.argb(popAlpha, 100, 181, 246))
                val popParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                            bottomMargin = elementMarginPx
                        }
                popLabel.layoutParams = popParams
                column.addView(popLabel)
            }

            // 4. Temperature at the bottom (e.g. "26°")
            val tempContainer =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                }

            val highTemp =
                TextView(context).apply {
                    text = day.tempHigh
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }
            TextViewCompat.setTextAppearance(highTemp, R.style.OverlayText)
            highTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize * tempSizeRatio)
            highTemp.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
            val highTempOffset = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, highTemp.textSize)
            highTemp.setPadding(0, highTempOffset, 0, -highTempOffset)
            highTemp.setTextColor(Color.argb(highTempAlpha, 255, 255, 255))
            tempContainer.addView(highTemp)

            if (day.tempLow.isNotEmpty()) {
                val separator =
                    TextView(context).apply {
                        text = " "
                        gravity = Gravity.CENTER
                    }
                tempContainer.addView(separator)

                val lowTemp =
                    TextView(context).apply {
                        text = day.tempLow
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    }
                TextViewCompat.setTextAppearance(lowTemp, R.style.OverlayText)
                lowTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveSize * tempSizeRatio)
                lowTemp.typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                val lowTempOffset = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, lowTemp.textSize)
                lowTemp.setPadding(0, lowTempOffset, 0, -lowTempOffset)
                lowTemp.setTextColor(Color.argb(lowTempAlpha, 255, 255, 255))
                tempContainer.addView(lowTemp)
            }

            column.addView(tempContainer)

            return column
        }

        private fun calculateIconSize(size: Float): Int {
            val textPaint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint

            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            val iconSize = textHeight * iconScale
            return iconSize.toInt()
        }
    }
