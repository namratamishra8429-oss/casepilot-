package com.casepilot.app

import android.app.Application
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class CasePilotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Purchases.configure(
            PurchasesConfiguration.Builder(
                this,
                "test_cIYtLPhADFdkZjUhCRZQDlDQHAG"
            ).build()
        )
    }
}