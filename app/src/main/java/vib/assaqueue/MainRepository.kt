package vib.assaqueue

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import vib.assaqueue.communication.Repository


class MainRepository private constructor(application: Application) : Repository(application) {

    private val _swVersion = MutableLiveData("")
    val swVersion: LiveData<String> get() = _swVersion

    private val _parameterValue = MutableLiveData("")
    val parameterValue: LiveData<String> get() = _parameterValue

    private val _parameterWriteResult = MutableLiveData("")
    val parameterWriteResult: LiveData<String> get() = _parameterWriteResult

    fun getSwVersion() {
        GlobalScope.launch {
            communicationService?.readSwVersion()?.let {
                _swVersion.postValue(it)
            }
        }
    }

    fun readParameter(parameterId : String) {
        val parameterInt = parameterId.toIntOrNull()
        if (parameterInt !in 0..59) {
            _parameterValue.value = "wrong parameter"
            return
        }
        GlobalScope.launch {
            communicationService?.readParameter(parameterInt!!)?.let {
                _parameterValue.postValue(it)
            }
        }
    }

    fun writeParameter(parameterId : String, parameterValue: String) {
        val parameterInt = parameterId.toIntOrNull()
        if (parameterInt !in 0..59) {
            _parameterWriteResult.value = "wrong parameter"
            return
        }
        val parameterValueInt = parameterValue.toIntOrNull()
        if (parameterValueInt == null) {
            _parameterValue.value = "wrong value"
            return
        }
        GlobalScope.launch {
            communicationService?.writeParameter(parameterInt!!, parameterValueInt!!)?.let {
                _parameterWriteResult.postValue(it)
            }
        }
    }

    companion object {
        private var instance: MainRepository? = null

        fun getInstance(application: Application): MainRepository {
            return instance ?: synchronized(this) {
                instance ?: MainRepository(application).also { instance = it }
            }
        }
    }
}