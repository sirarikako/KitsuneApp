package com.sirarikako.kitsune

import android.app.Application
import androidx.room.Room
import com.sirarikako.kitsune.core.data.*

class KitsuneApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, KitsuneDatabase::class.java, "kitsune.db").build() }
    val repository by lazy { KitsuneRepository(database) }
}
