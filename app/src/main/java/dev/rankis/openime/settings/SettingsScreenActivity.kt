package dev.rankis.openime.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.rankis.openime.R

/** Localized, edge-to-edge settings screen; sub-screens get a top bar with a back button. */
abstract class SettingsScreenActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    protected fun setScreenContent(@LayoutRes layout: Int, @StringRes title: Int? = null) {
        enableEdgeToEdge()
        setContentView(layout)
        padForSystemBars(findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        if (title != null) {
            findViewById<TextView>(R.id.topBarTitle).setText(title)
            findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
        }
    }

    private fun padForSystemBars(root: View) {
        val start = root.paddingStart
        val top = root.paddingTop
        val end = root.paddingEnd
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPaddingRelative(start + insets.left, top + insets.top, end + insets.right, bottom + insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
