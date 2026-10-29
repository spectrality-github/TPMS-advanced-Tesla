package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanResult
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.ScannerRecord
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlin.math.roundToInt

@ConsistentCopyVisibility
@Suppress("MagicNumber")
internal data class RawMichelinTrackConnect private constructor(
    private val address: String,
    private val rssi: Int,
    private val manufacturerData: ByteArray,
    private val offset: Int,
) : Raw {

    private val frameType get() = manufacturerData[1 + offset].toInt() and 0xFF

    private fun temperature() =
        ((manufacturerData[2 + offset].toInt() and 0xFF) - 60).toFloat().celsius

    private fun voltage() =
        ((manufacturerData[3 + offset].toInt() and 0xFF) / 100f) + 1f

    private fun pressure() = when (frameType) {
        0x03, 0x04, 0x0C -> pressureAt(4 + offset)
        0x05 -> pressureAt(12 + offset)
        0x06 -> 0f.bar
        else -> error("Unsupported Michelin Track Connect frame type: $frameType")
    }

    private fun pressureAt(index: Int) = (
        ((manufacturerData[index + 1].toInt() and 0xFF) shl 8) or
            (manufacturerData[index].toInt() and 0xFF)
        )
        .div(1000f)
        .minus(1f)
        .coerceAtLeast(0f)
        .bar

    private fun id() = address.uppercase().hashCode()

    override fun asTyre(): ScannerRecord = Tyre.Unlocated(
        now(),
        rssi,
        id(),
        pressure(),
        temperature(),
        (voltage() * 10f).roundToInt().toUShort(),
        voltage() <= 2.1f,
        macAddress = address.uppercase(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawMichelinTrackConnect

        return address == other.address &&
            rssi == other.rssi &&
            manufacturerData.contentEquals(other.manufacturerData)
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + rssi
        result = 31 * result + manufacturerData.contentHashCode()
        return result
    }

    companion object {
        internal const val MANUFACTURER_ID = 0x0828

        @Suppress("ReturnCount")
        operator fun invoke(scanResult: ScanResult): RawMichelinTrackConnect? {
            val data = scanResult.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
                ?: return null
            val offset = if (data.size >= 2 && data[0] == 0x28.toByte() && data[1] == 0x08.toByte()) 2 else 0
            if (data.size < offset + 4) return null
            if ((data[offset].toInt() and 0xFF) != 0x01) return null

            val frameType = data[offset + 1].toInt() and 0xFF
            val requiredLength = when (frameType) {
                0x03, 0x04, 0x0C -> offset + 6
                0x05 -> offset + 14
                0x06 -> offset + 4
                else -> return null
            }
            if (data.size < requiredLength) return null

            return RawMichelinTrackConnect(
                scanResult.device.address,
                scanResult.rssi,
                data,
                offset,
            )
        }
    }
}
