package com.mcqueary.tunerapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TunerViewModel: ViewModel() {
    val note = MutableLiveData<String>()
    val noteBar = MutableLiveData<Int>()
    val noteBarColor = MutableLiveData<Int>()
    val frequency = MutableLiveData<String>()

    init {
        note.value = "☺"
        noteBar.value = 0
        frequency.value = "0.0"
    }
}