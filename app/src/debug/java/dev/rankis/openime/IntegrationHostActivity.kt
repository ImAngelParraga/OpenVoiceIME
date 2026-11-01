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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        editor = EditText(this).apply {
            showSoftInputOnFocus = false
            id = View.generateViewId()
        }
        setContentView(editor)
    }
}
