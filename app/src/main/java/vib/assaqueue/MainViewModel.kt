package vib.assaqueue

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mainRepo = MainRepository.getInstance(application)

    val swVersion = mainRepo.swVersion
    val parameterValue = mainRepo.parameterValue


    fun getSwVersion() {
        mainRepo.getSwVersion()
    }
    
    fun readParameter(parameterId : String) {
        mainRepo.readParameter(parameterId)
    }

    fun writeParameter(parameterId : String, parameterValue: String) {
        mainRepo.writeParameter(parameterId, parameterValue)
    }
}