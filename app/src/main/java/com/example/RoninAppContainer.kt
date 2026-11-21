package com.example

import android.content.Context
import com.example.audio.SoundEngine
import com.example.data.local.RoninDatabase
import com.example.data.remote.CloudBackupRepository
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestore

class RoninAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { RoninDatabase.getInstance(appContext) }
    val repository by lazy { StudioRepository(appContext, database.projectDao()) }
    val soundEngine by lazy { SoundEngine(appContext) }
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
    }
    val cloudBackupRepository by lazy { CloudBackupRepository(firestore) }
}
