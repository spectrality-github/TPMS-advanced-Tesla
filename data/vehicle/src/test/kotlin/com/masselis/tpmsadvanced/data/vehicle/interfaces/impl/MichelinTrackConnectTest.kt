package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.utils.mockScanRecord
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.utils.mockScanResult
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
internal class MichelinTrackConnectTest {

    private fun decode(
        manufacturerData: String,
        manufacturerId: Int = RawMichelinTrackConnect.MANUFACTURER_ID,
        address: String = "BC:03:00:00:00:01",
    ) = RawMichelinTrackConnect(
        mockScanResult(
            mockScanRecord(
                mockManufacturerData = manufacturerData.hexToByteArray(),
                mockManufacturerId = manufacturerId,
            ),
            mockAddress = address,
        )
    )

    @Test
    fun `standard pressure frame is decoded with its mac address`() {
        decode("01035AAAB80B")
            ?.asTyre()
            .let { assertIs<Tyre.Unlocated>(it) }
            .also {
                assertEquals(2f, it.pressure.asBar())
                assertEquals(30f, it.temperature.celsius)
                assertEquals(27u, it.battery)
                assertEquals("BC:03:00:00:00:01", it.macAddress)
                assertEquals("BC:03:00:00:00:01".hashCode(), it.sensorId)
            }
    }

    @Test
    fun `extended pressure frame reads pressure at its extended offset`() {
        decode("01055AAAB800000000000000C409")
            ?.asTyre()
            .let { assertIs<Tyre.Unlocated>(it) }
            .also { assertEquals(1.5f, it.pressure.asBar()) }
    }

    @Test
    fun `frame without pressure data reports zero pressure`() {
        decode("01065AA0")
            ?.asTyre()
            .let { assertIs<Tyre.Unlocated>(it) }
            .also { assertEquals(0f, it.pressure.kpa) }
    }

    @Test
    fun `truncated and unsupported frames are ignored`() {
        assertNull(decode("01035AAAB8"))
        assertNull(decode("01075AAAB80B"))
        assertNull(decode("01055AAAB8000000000000"))
    }

    @Test
    fun `another manufacturer is ignored`() {
        assertNull(decode("01035AAAB80B", manufacturerId = 0x022B))
    }
}
