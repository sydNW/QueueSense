
package com.project.queuesense.data.local

import android.content.Context

object TokenStorageProvider {

    private lateinit var storage: TokenStorage

    fun initialize(context: Context) {
        storage = TokenStorage(context.applicationContext)
    }

    fun getInstance(): TokenStorage {
        check(::storage.isInitialized) {
            "TokenStorageProvider has not been initialized"
        }
        return storage
    }
}
