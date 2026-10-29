package com.masselis.tpmsadvanced.data.vehicle.model

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

public sealed interface ScannerRecord : Parcelable {
    public val timestamp: Double
    public val rssi: Int
    public val sensorId: Int
    public val infos: Set<ManufacturerSpecificInfo>
    public val macAddress: String? get() = null

    @Parcelize
    public data class TeslaId(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val infos: Set<ManufacturerSpecificInfo> = emptySet(),
        val device: BluetoothDevice,
        override val macAddress: String = device.address,
    ) : ScannerRecord {
        public constructor(
            timestamp: Double,
            rssi: Int,
            sensorId: Int,
            device: BluetoothDevice,
            vararg infos: ManufacturerSpecificInfo,
        ) : this(timestamp, rssi, sensorId, infos.toSet(), device)
    }

}
