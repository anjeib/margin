package ru.anjeib.margin.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Android Auto Backup to the user's Google account (Google Drive app data), with an off switch in Settings.
 * The rules of what goes in stay in xml/backup_rules and xml/data_extraction_rules; this only says «not now».
 */
class MarginBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        if (getSharedPreferences("margin", MODE_PRIVATE).getBoolean("googleBackup", true)) super.onFullBackup(data)
    }

    // Key-value backup is not used — Auto Backup only.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {}
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {}
}
