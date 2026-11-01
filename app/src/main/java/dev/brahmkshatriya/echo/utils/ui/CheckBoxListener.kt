package dev.brahmkshatriya.echo.utils.ui

import android.widget.CompoundButton

class CheckBoxListener(private val block: (Boolean) -> Unit) :
    CompoundButton.OnCheckedChangeListener {
    var enabled = true
    private var checked = false
    private fun check(isChecked: Boolean) {
        if (checked == isChecked) return
        block(isChecked)
    }

    override fun onCheckedChanged(button: CompoundButton, isChecked: Boolean) {
        if (enabled) check(isChecked)
        checked = isChecked
    }
}
