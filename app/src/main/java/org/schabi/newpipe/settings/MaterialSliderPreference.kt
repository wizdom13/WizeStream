package org.schabi.newpipe.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import org.schabi.newpipe.R

/** Uses the existing integer preference contract with a Material slider. */
class MaterialSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private val minimum: Int
    private val maximum: Int
    private var initialValue = 0
    private var currentValue = 0
    private var observedPreferences: SharedPreferences? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == null || changedKey == key) refreshPersistedValue()
    }
    var value: Int
        get() = currentValue
        set(newValue) {
            updateValue(newValue, persist = true)
        }

    init {
        val bounds = context.obtainStyledAttributes(attrs, R.styleable.MaterialSliderPreference)
        minimum = bounds.getInt(R.styleable.MaterialSliderPreference_min, 0)
        maximum = bounds.getInt(R.styleable.MaterialSliderPreference_android_max, 100).coerceAtLeast(minimum)
        bounds.recycle()
        val defaults = context.obtainStyledAttributes(attrs, intArrayOf(androidx.preference.R.attr.defaultValue, android.R.attr.defaultValue))
        initialValue = defaults.getInt(if (defaults.hasValue(0)) 0 else 1, minimum)
        defaults.recycle()
        layoutResource = R.layout.preference_material_slider
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, 0)

    override fun onSetInitialValue(defaultValue: Any?) {
        if (defaultValue is Int) initialValue = defaultValue
        value = getPersistedInt(initialValue)
    }

    override fun onAttached() {
        super.onAttached()
        if (shouldPersist()) {
            observedPreferences = sharedPreferences
            observedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
            refreshPersistedValue()
        }
    }

    override fun onDetached() {
        observedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observedPreferences = null
        super.onDetached()
    }

    private fun refreshPersistedValue() {
        if (shouldPersist()) updateValue(getPersistedInt(initialValue), persist = false)
    }

    private fun updateValue(newValue: Int, persist: Boolean) {
        val bounded = newValue.coerceIn(minimum, maximum)
        if (currentValue != bounded) {
            currentValue = bounded
            // External writers already committed the value; refreshing must not write it back.
            if (persist) persistInt(bounded)
            notifyChanged()
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val parent = super.onSaveInstanceState()
        if (isPersistent) return parent
        return Bundle().apply {
            putParcelable("parent", parent)
            putInt("value", value)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("parent"))
            value = state.getInt("value")
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isFocusable = false
        val slider = holder.findViewById(R.id.preference_material_slider) as Slider
        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = minimum.toFloat()
        slider.valueTo = maximum.coerceAtLeast(minimum + 1).toFloat()
        slider.stepSize = 1f
        slider.value = value.coerceIn(minimum, maximum).toFloat()
        slider.isEnabled = isEnabled && minimum < maximum
        slider.contentDescription = title
        var tracking = false
        fun persistSelection() {
            val proposed = slider.value.toInt()
            if (proposed != value) {
                if (callChangeListener(proposed)) value = proposed else slider.value = value.toFloat()
            }
        }
        slider.addOnChangeListener { _, _, fromUser ->
            if (fromUser && !tracking) persistSelection()
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                tracking = true
            }
            override fun onStopTrackingTouch(slider: Slider) {
                tracking = false
                persistSelection()
            }
        })
    }
}
