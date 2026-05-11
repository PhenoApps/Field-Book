package com.fieldbook.tracker.database.repository

import com.fieldbook.tracker.database.dao.spectral.DeviceDao
import com.fieldbook.tracker.database.dao.spectral.ProtocolDao
import com.fieldbook.tracker.database.dao.spectral.SpectralDao
import com.fieldbook.tracker.database.dao.spectral.UriDao
import com.fieldbook.tracker.database.models.spectral.Device
import com.fieldbook.tracker.database.models.spectral.Protocol
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import javax.inject.Inject

class SpectralRepository @Inject constructor(
    private val spectralDao: SpectralDao,
    private val protocolDao: ProtocolDao,
    private val deviceDao: DeviceDao,
    private val uriDao: UriDao
) {

    fun insertProtocol(protocol: Protocol): Long {
        return protocolDao.insertProtocol(protocol)
    }

    fun insertDevice(device: Device): Long {
        return deviceDao.insertDevice(device)
    }

    fun insertUri(uri: String): Long {
        return uriDao.insertUri(uri)
    }

    fun insertSpectralFact(spectralFact: SpectralFact): Long {
        return spectralDao.insertSpectralFact(spectralFact)
    }

    fun updateFact(fact: SpectralFact) {
        spectralDao.updateFact(fact)
    }

    fun getSpectralFacts(observationIds: List<Int>): List<SpectralFact> {
        return spectralDao.getSpectralFacts(observationIds)
    }

    fun getSpectralFacts(studyId: Int): List<SpectralFact> {
        return spectralDao.getSpectralFacts(studyId)
    }

    fun getSpectralFactById(id: Int): SpectralFact? {
        return spectralDao.getSpectralFactById(id)
    }

    fun getDeviceId(name: String, address: String): Int {
        return deviceDao.getDeviceId(name, address)
    }

    fun getDevice(id: Int? = null, name: String? = null, address: String? = null): Device? {
        return deviceDao.getDevice(id, name, address)
    }

    fun getUri(id: Int): String? {
        return uriDao.getUri(id)
    }

    fun getUriId(uri: String): Int {
        return uriDao.getUriId(uri)
    }

    fun getProtocolId(title: String): Int {
        return protocolDao.getProtocolId(title)
    }

    fun deleteSpectralFact(fact: SpectralFact) {
        spectralDao.deleteSpectralFact(fact)
    }

    fun deleteSpectralObservation(studyId: String, plotId: String, traitId: String, value: String) {
        spectralDao.deleteSpectralObservation(studyId, plotId, traitId, value)
    }
}
