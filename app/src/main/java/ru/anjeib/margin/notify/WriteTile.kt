package ru.anjeib.margin.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import ru.anjeib.margin.MainActivity

/** «Записать» in the quick settings: one tap from anywhere to the field with the keyboard up. */
class WriteTile : TileService() {
    // A tile with no state of its own shows as «Недоступно» and ignores taps: it is an action, always ready.
    override fun onStartListening() {
        qsTile?.apply { state = android.service.quicksettings.Tile.STATE_INACTIVE; updateTile() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_WRITE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
