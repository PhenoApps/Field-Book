package com.fieldbook.tracker.database.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.database.models.spectral.Device
import com.fieldbook.tracker.database.models.spectral.Protocol
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import com.fieldbook.tracker.database.repository.SpectralRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpectralViewModel(private val repository: SpectralRepository) : ViewModel() {

    companion object {
        private const val TAG = "SpectralViewModel"
    }

    //insert spectral fact and return id
    fun insertSpectralFact(fact: SpectralFact): Long {
        return repository.insertSpectralFact(fact)
    }

    fun insertDevice(device: Device): Int {
        return repository.insertDevice(device).toInt()
    }

    fun insertUri(uri: String): Int {
        return repository.insertUri(uri).toInt()
    }

    fun insertProtocol(protocol: Protocol): Int {
        return repository.insertProtocol(protocol).toInt()
    }

    fun updateFact(fact: SpectralFact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateFact(fact)
        }
    }

    //get functions to return spectral database objects
    fun getSpectralFacts(ids: List<Int>): List<SpectralFact> {
        return repository.getSpectralFacts(ids)
    }

    fun getDeviceId(name: String, address: String): Int {
        return repository.getDeviceId(name, address)
    }

    fun getDevice(id: Int? = null, name: String? = null, address: String? = null): Device? {
        return repository.getDevice(id, name, address)
    }

    fun getUriId(uri: String): Int {
        return repository.getUriId(uri)
    }

    fun getProtocolId(title: String): Int {
        return repository.getProtocolId(title)
    }

    fun deleteSpectralFact(fact: SpectralFact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSpectralFact(fact)
        }
    }

    fun deleteSpectralObservation(studyId: String, plotId: String, traitId: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSpectralObservation(studyId, plotId, traitId, value)
        }
    }
}
