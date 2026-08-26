package com.androtim.timetable

import android.app.Application
import com.androtim.timetable.sync.SyncWorker

class TimetableApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
