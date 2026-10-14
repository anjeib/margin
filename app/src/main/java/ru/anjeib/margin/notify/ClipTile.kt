package ru.anjeib.margin.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import ru.anjeib.margin.ClipActivity

/** «Во Входящие» in the quick settings (owner, 14 окт): what is copied anywhere goes to «Входящие» in one tap. */
class ClipTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = android.service.quicksettings.Tile.STATE_INACTIVE; updateTile() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        // Only an app in front may read the clipboard (Android 10+): a window of Margin opens for a blink and reads it.
        val i = Intent(this, ClipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
