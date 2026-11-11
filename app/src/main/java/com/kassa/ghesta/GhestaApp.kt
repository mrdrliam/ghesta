package com.kassa.ghesta

import android.app.Application
import com.kassa.ghesta.data.Account
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.notify.Reminders

class GhestaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Account.init(this)
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
    }
}
