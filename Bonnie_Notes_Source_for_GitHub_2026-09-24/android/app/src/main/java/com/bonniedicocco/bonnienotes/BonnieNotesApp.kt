package com.bonniedicocco.bonnienotes

import android.app.Application
import com.bonniedicocco.bonnienotes.data.AppDatabase

class BonnieNotesApp : Application() {
    val database by lazy { AppDatabase.get(this) }
}

