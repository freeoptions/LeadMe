package com.leadme.app

import android.app.Application
import androidx.room.Room
import com.leadme.app.data.local.LeadMeDatabase
import com.leadme.app.data.repository.MapRepository

class LeadMeApplication : Application() {
    val repository: MapRepository by lazy {
        MapRepository(Room.databaseBuilder(this, LeadMeDatabase::class.java, "leadme.db").build())
    }
}
