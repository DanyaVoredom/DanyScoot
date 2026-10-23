package ru.dany.scoot.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.dany.scoot.R

class DeviceClassifierTest {

    @Test
    fun classifyByUartService() {
        val kind = DeviceClassifier.classify("Scooter", setOf(BleTransport.UART_SERVICE))
        assertEquals(DeviceKind.SCOOTER, kind)
    }

    @Test
    fun classifyEdifierByName() {
        assertEquals(DeviceKind.HEADSET, DeviceClassifier.classify("EDIFIER W820NB", emptySet()))
        assertEquals(DeviceKind.HEADSET, DeviceClassifier.classify("edifier", null))
    }

    @Test
    fun classifyEdifierByService() {
        val kind = DeviceClassifier.classify("Headset", setOf(DeviceClassifier.EDIFIER_SERVICE))
        assertEquals(DeviceKind.HEADSET, kind)
    }

    @Test
    fun classifyUnknown() {
        assertEquals(DeviceKind.OTHER, DeviceClassifier.classify("Unknown", emptySet()))
    }

    @Test
    fun distanceAtReferenceRssi() {
        assertEquals(1.0, DeviceClassifier.distanceMeters(-59), 0.001)
    }

    @Test
    fun distanceGrowsWithWeakSignal() {
        assertEquals(10.0, DeviceClassifier.distanceMeters(-79), 0.001)
        assertEquals(100.0, DeviceClassifier.distanceMeters(-99), 0.001)
    }

    @Test
    fun distanceIsClamped() {
        assertEquals(0.2, DeviceClassifier.distanceMeters(-10), 0.001)
        assertEquals(120.0, DeviceClassifier.distanceMeters(0), 0.001)
    }

    @Test
    fun formatDistance() {
        assertEquals("1.0", DeviceClassifier.formatDistance(-59))
        assertEquals("10", DeviceClassifier.formatDistance(-79))
    }

    @Test
    fun sortFallsBackToDistance() {
        assertEquals(DeviceSort.DISTANCE, DeviceSort.fromId("unknown"))
        assertEquals(DeviceSort.NAME, DeviceSort.fromId("name"))
    }

    @Test
    fun labels() {
        assertEquals(R.string.device_kind_scooter, DeviceClassifier.labelRes(DeviceKind.SCOOTER))
        assertEquals(R.string.device_kind_headset, DeviceClassifier.labelRes(DeviceKind.HEADSET))
        assertEquals(R.string.device_kind_ble, DeviceClassifier.labelRes(DeviceKind.OTHER))
    }
}
