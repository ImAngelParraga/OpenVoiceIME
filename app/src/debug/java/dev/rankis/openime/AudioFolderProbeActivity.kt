package dev.rankis.openime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.TextView

class AudioFolderProbeActivity : Activity() {
    var selectedTree: Uri? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Synthetic audio folder access check" })
    }

    fun chooseFolder(initialUri: Uri) {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri), 1)
    }

    @Deprecated("Uses the Activity result callback for the isolated storage check")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            selectedTree = data?.data
            selectedTree?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
