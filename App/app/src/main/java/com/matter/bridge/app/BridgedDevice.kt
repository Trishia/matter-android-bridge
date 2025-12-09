package com.matter.bridge.app

sealed class BridgedDevice(
    var name: String,
    val endpoint: Int,
    val type: DeviceType,
    var isReachable: Boolean = true,
    val isBridgedNode: Boolean = false  // True if has BRIDGED_NODE device type
) {
    // Light device with OnOff cluster
    class Light(name: String, endpoint: Int, var isOn: Boolean = false, isBridgedNode: Boolean = true) : 
        BridgedDevice(name, endpoint, DeviceType.LIGHT, isReachable = true, isBridgedNode = isBridgedNode) {
        
        fun setOnOff(bridgeApp: BridgeApp, value: Boolean) {
            isOn = value
            // Convert boolean to 1-byte array (0 or 1)
            val bytes = byteArrayOf(if (value) 1.toByte() else 0.toByte())
            bridgeApp.updateClusterAttribute(
                endpoint,
                MatterConstants.OnOff.CLUSTER_ID,
                MatterConstants.OnOff.Attributes.ON_OFF,
                bytes
            )
        }
    }
        
    // Temperature Sensor with TemperatureMeasurement cluster
    class TemperatureSensor(name: String, endpoint: Int, var temperature: Int = 0, isBridgedNode: Boolean = true) :
        BridgedDevice(name, endpoint, DeviceType.TEMP_SENSOR, isReachable = true, isBridgedNode = isBridgedNode) {
        
        fun setTemperature(bridgeApp: BridgeApp, value: Int) {
            temperature = value
            // Convert int to 2-byte array (int16) - Little Endian
            val bytes = ByteArray(2)
            bytes[0] = (value and 0xFF).toByte()
            bytes[1] = ((value shr 8) and 0xFF).toByte()
            
            bridgeApp.updateClusterAttribute(
                endpoint,
                MatterConstants.TemperatureMeasurement.CLUSTER_ID,
                MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE,
                bytes
            )
        }
    }
    
    // Humidity Sensor with RelativeHumidityMeasurement cluster
    class HumiditySensor(name: String, endpoint: Int, var humidity: Int = 0, isBridgedNode: Boolean = true) :
        BridgedDevice(name, endpoint, DeviceType.HUMIDITY_SENSOR, isReachable = true, isBridgedNode = isBridgedNode) {
        
        fun setHumidity(bridgeApp: BridgeApp, value: Int) {
            humidity = value
            // Convert int to 2-byte array (uint16) - Little Endian
            val bytes = ByteArray(2)
            bytes[0] = (value and 0xFF).toByte()
            bytes[1] = ((value shr 8) and 0xFF).toByte()
            
            bridgeApp.updateClusterAttribute(
                endpoint,
                MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID,
                MatterConstants.RelativeHumidityMeasurement.Attributes.MEASURED_VALUE,
                bytes
            )
        }
    }

    // Composed Device with PowerSource cluster (parent for child devices)
    class Composed(
        name: String, 
        endpoint: Int,
        var batteryChargeLevel: Int = 0,
        isBridgedNode: Boolean = true
    ) : BridgedDevice(name, endpoint, DeviceType.COMPOSED_DEVICE, isReachable = true, isBridgedNode = isBridgedNode) {
        
        fun setBatteryChargeLevel(bridgeApp: BridgeApp, value: Int) {
            batteryChargeLevel = value
            // Convert to 1-byte enum
            val bytes = byteArrayOf(value.toByte())
            
            bridgeApp.updateClusterAttribute(
                endpoint,
                MatterConstants.PowerSource.CLUSTER_ID,
                MatterConstants.PowerSource.Attributes.BAT_CHARGE_LEVEL,
                bytes
            )
        }
    }

    // Generic Device with arbitrary clusters
    class Generic(
        name: String, 
        endpoint: Int, 
        val clusterIds: List<Int>,
        isBridgedNode: Boolean = false
    ) : BridgedDevice(name, endpoint, DeviceType.GENERIC, isReachable = true, isBridgedNode = isBridgedNode) {
        
        // Store attributes as generic objects to support different types
        private val attributes = mutableMapOf<Int, MutableMap<Int, Any>>()
        
        fun updateAttribute(bridgeApp: BridgeApp, clusterId: Int, attributeId: Int, value: Any) {
            attributes.getOrPut(clusterId) { mutableMapOf() }[attributeId] = value
            
            val bytes: ByteArray = when (value) {
                is Boolean -> byteArrayOf(if (value) 1.toByte() else 0.toByte())
                is Byte -> byteArrayOf(value)
                is Short -> {
                    val b = ByteArray(2)
                    b[0] = (value.toInt() and 0xFF).toByte()
                    b[1] = ((value.toInt() shr 8) and 0xFF).toByte()
                    b
                }
                is Int -> {
                    // Assuming 4-byte integer (int32/uint32)
                    val b = ByteArray(4)
                    b[0] = (value and 0xFF).toByte()
                    b[1] = ((value shr 8) and 0xFF).toByte()
                    b[2] = ((value shr 16) and 0xFF).toByte()
                    b[3] = ((value shr 24) and 0xFF).toByte()
                    b
                }
                is Long -> {
                    // Assuming 8-byte integer (int64/uint64)
                    val b = ByteArray(8)
                    for (i in 0..7) {
                        b[i] = ((value shr (i * 8)) and 0xFF).toByte()
                    }
                    b
                }
                is String -> value.toByteArray(Charsets.UTF_8)
                is ByteArray -> value
                else -> throw IllegalArgumentException("Unsupported attribute type: ${value::class.java}")
            }
            
            bridgeApp.updateClusterAttribute(endpoint, clusterId, attributeId, bytes)
        }
        
        fun getAttribute(clusterId: Int, attributeId: Int): Any? {
            return attributes[clusterId]?.get(attributeId)
        }
    }
}

enum class DeviceType {
    LIGHT,
    TEMP_SENSOR,
    HUMIDITY_SENSOR,
    COMPOSED_DEVICE,
    POWER_SOURCE,
    GENERIC
}
