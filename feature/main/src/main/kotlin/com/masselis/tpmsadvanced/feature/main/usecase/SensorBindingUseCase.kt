package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus

internal interface SensorBindingUseCase {

    fun boundSensor(): StateFlow<Sensor?>
    fun boundSensor(location: Location): StateFlow<Sensor?>
    fun configuredMacAddress(location: Location): StateFlow<String?>
    fun boundVehicle(sensor: Sensor): Flow<Vehicle?>

    suspend fun bind(sensor: Sensor)
    suspend fun saveMacAddress(location: Location, macAddress: String)

    class Impl(
        private val currentVehicle: Vehicle,
        private val vehicleDatabase: VehicleDatabase,
        private val sensorDatabase: SensorDatabase,
        private val currentLocation: Location,
        private val scope: CoroutineScope,
    ) : SensorBindingUseCase {

        private val boundSensor = sensorDatabase.selectByVehicleAndLocation(
            currentVehicle.uuid,
            currentLocation
        ).asStateFlow(scope + IO, Eagerly)
        private val boundSensors = mutableMapOf(currentLocation to boundSensor)
        private val configuredMacAddresses = mutableMapOf<Location, StateFlow<String?>>()

        override fun boundSensor(): StateFlow<Sensor?> = boundSensor

        override fun boundSensor(location: Location): StateFlow<Sensor?> = synchronized(boundSensors) {
            boundSensors.getOrPut(location) {
                sensorDatabase
                    .selectByVehicleAndLocation(currentVehicle.uuid, location)
                    .asStateFlow(scope + IO, Eagerly)
            }
        }

        override fun configuredMacAddress(location: Location): StateFlow<String?> =
            synchronized(configuredMacAddresses) {
                configuredMacAddresses.getOrPut(location) {
                    sensorDatabase
                        .selectMacAddressByVehicleAndLocation(currentVehicle.uuid, location)
                        .asStateFlow(scope + IO, Eagerly)
                }
            }

        override fun boundVehicle(sensor: Sensor) = vehicleDatabase
            .selectBySensorId(sensor.id)
            .asFlow()

        override suspend fun bind(sensor: Sensor) =
            sensorDatabase.upsert(sensor, currentVehicle.uuid)

        override suspend fun saveMacAddress(location: Location, macAddress: String) =
            sensorDatabase.saveMacAddress(currentVehicle.uuid, location, macAddress)
    }

    object NoOp : SensorBindingUseCase {
        override fun boundSensor(): StateFlow<Sensor?> = MutableStateFlow(null)
        override fun boundSensor(location: Location): StateFlow<Sensor?> = MutableStateFlow(null)
        override fun configuredMacAddress(location: Location): StateFlow<String?> = MutableStateFlow(null)
        override fun boundVehicle(sensor: Sensor): Flow<Vehicle?> = MutableStateFlow(null)
        override suspend fun bind(sensor: Sensor) {}
        override suspend fun saveMacAddress(location: Location, macAddress: String) {}
    }
}
