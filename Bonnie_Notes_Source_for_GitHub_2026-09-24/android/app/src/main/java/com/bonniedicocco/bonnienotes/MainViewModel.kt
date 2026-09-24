package com.bonniedicocco.bonnienotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bonniedicocco.bonnienotes.data.AppDatabase
import com.bonniedicocco.bonnienotes.data.MeetingEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).meetingDao()
    val query = MutableStateFlow("")
    val meetings = query.flatMapLatest { value ->
        if (value.isBlank()) dao.observeAll() else dao.search(value.trim())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(value: String) { query.value = value }
}

