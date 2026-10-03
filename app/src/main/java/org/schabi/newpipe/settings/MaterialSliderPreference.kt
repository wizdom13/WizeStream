package org.schabi.newpipe.settings

import android.content.Context
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
    var value: Int = 0
        set(newValue) {
            val bounded = newValue.coerceIn(minimum, maximum)
            if (field != bounded) {
                field = bounded
                persistInt(bounded)
                notifyChanged()
            }
        }

    init {
        val bounds = context.obtainStyledAttributes(attrs, intArrayOf(androidx.preference.R.attr.min, android.R.attr.max))
        minimum = bounds.getInt(0, 0)
        maximum = bounds.getInt(1, 100).coerceAtLeast(minimum)
        bounds.recycle()
        layoutResource = R.layout.preference_material_slider
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, 0)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt(defaultValue as? Int ?: minimum)
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
