package dev.rankis.openime

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.view.View
import android.widget.EditText

class IntegrationHostActivity : Activity() {
    lateinit var editor: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val normalEditor = intent.getBooleanExtra("normal_editor", false)
        window.setSoftInputMode(if (normalEditor) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            else WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        editor = EditText(this).apply {
            showSoftInputOnFocus = normalEditor
            id = View.generateViewId()
        }
        setContentView(editor)
    }
}
