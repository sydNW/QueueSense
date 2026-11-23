
package com.project.queuesense

import android.app.Application
import com.project.queuesense.data.local.TokenStorageProvider

class QueueSenseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenStorageProvider.initialize(this)
    }
}
