package com.matter.bridge.app

object DeviceFactory {
    
    private var bridgeApp: BridgeApp? = null
    
    fun initialize(app: BridgeApp) {
        bridgeApp = app
    }
    
    fun createLight(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.Light {
        val clusters = intArrayOf(MatterConstants.OnOff.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.ON_OFF_LIGHT, MatterConstants.DeviceType.BRIDGED_NODE)
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.OnOff.CLUSTER_ID, MatterConstants.OnOff.Attributes.ON_OFF, MatterConstants.AttributeType.BOOLEAN, 1, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.WRITABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.OnOff.CLUSTER_ID, MatterConstants.OnOff.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.Light(name, endpoint, isBridgedNode = isBridgedNode)
    }
    
    fun createTempSensor(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.TemperatureSensor {
        val clusters = intArrayOf(MatterConstants.TemperatureMeasurement.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.TEMP_SENSOR, MatterConstants.DeviceType.BRIDGED_NODE)
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MIN_MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MAX_MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.TemperatureSensor(name, endpoint, isBridgedNode = isBridgedNode)
    }

    fun createComposedTempSensor(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.TemperatureSensor {
        val clusters = intArrayOf(MatterConstants.TemperatureMeasurement.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.TEMP_SENSOR)

        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MIN_MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.MAX_MEASURED_VALUE, MatterConstants.AttributeType.INT16S, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.TemperatureMeasurement.CLUSTER_ID, MatterConstants.TemperatureMeasurement.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )

        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.TemperatureSensor(name, endpoint, isBridgedNode = isBridgedNode)
    }
    
    fun createHumiditySensor(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.HumiditySensor {
        val clusters = intArrayOf(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.HUMIDITY_SENSOR, MatterConstants.DeviceType.BRIDGED_NODE)
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MIN_MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MAX_MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.HumiditySensor(name, endpoint, isBridgedNode = isBridgedNode)
    }

    fun createComposedHumiditySensor(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.HumiditySensor {
        val clusters = intArrayOf(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.HUMIDITY_SENSOR)  // No BRIDGED_NODE

        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MIN_MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.MAX_MEASURED_VALUE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID, MatterConstants.RelativeHumidityMeasurement.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )

        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.HumiditySensor(name, endpoint, isBridgedNode = isBridgedNode)
    }
    
    fun createDoorLock(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.Generic {
        val clusters = intArrayOf(MatterConstants.DoorLock.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.BRIDGED_NODE)
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.DoorLock.CLUSTER_ID, MatterConstants.DoorLock.Attributes.LOCK_STATE, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.DoorLock.CLUSTER_ID, MatterConstants.DoorLock.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.Generic(name, endpoint, clusters.toList(), isBridgedNode = isBridgedNode)
    }
    
    fun createGenericOnOffDevice(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.Generic {
        val clusters = intArrayOf(MatterConstants.OnOff.CLUSTER_ID)
        val deviceTypes = intArrayOf(MatterConstants.DeviceType.ON_OFF_LIGHT, MatterConstants.DeviceType.BRIDGED_NODE)
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.OnOff.CLUSTER_ID, MatterConstants.OnOff.Attributes.ON_OFF, MatterConstants.AttributeType.BOOLEAN, 1, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.WRITABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.OnOff.CLUSTER_ID, MatterConstants.OnOff.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.Generic(name, endpoint, clusters.toList(), isBridgedNode = isBridgedNode)
    }
    
    /**
     * Create a composed device parent with PowerSource cluster.
     * Child devices should be created separately with their parentEndpointId set to this device's endpoint.
     */
    fun createComposedDevice(name: String, endpoint: Int, parentEndpointId: Int = 1): BridgedDevice.Composed {
        // Parent device with PowerSource cluster
        val clusters = intArrayOf(MatterConstants.PowerSource.CLUSTER_ID)
        val deviceTypes = intArrayOf(
            MatterConstants.DeviceType.BRIDGED_NODE,
            MatterConstants.DeviceType.POWER_SOURCE
        )
        
        val attributes = arrayOf(
            ClusterAttribute(MatterConstants.PowerSource.CLUSTER_ID, MatterConstants.PowerSource.Attributes.BAT_CHARGE_LEVEL, MatterConstants.AttributeType.ENUM8, 1, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.PowerSource.CLUSTER_ID, MatterConstants.PowerSource.Attributes.ORDER, MatterConstants.AttributeType.INT8U, 1, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.PowerSource.CLUSTER_ID, MatterConstants.PowerSource.Attributes.STATUS, MatterConstants.AttributeType.ENUM8, 1, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.PowerSource.CLUSTER_ID, MatterConstants.PowerSource.Attributes.DESCRIPTION, MatterConstants.AttributeType.CHAR_STRING, 32, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE),
            ClusterAttribute(MatterConstants.PowerSource.CLUSTER_ID, MatterConstants.PowerSource.Attributes.CLUSTER_REVISION, MatterConstants.AttributeType.INT16U, 2, MatterConstants.AttributeMask.READABLE or MatterConstants.AttributeMask.EXTERNAL_STORAGE)
        )
        
        bridgeApp?.addBridgedDevice(endpoint, parentEndpointId, name, clusters, attributes, deviceTypes)
        
        // Check if device has BRIDGED_NODE device type
        val isBridgedNode = deviceTypes.contains(MatterConstants.DeviceType.BRIDGED_NODE)
        return BridgedDevice.Composed(name, endpoint, isBridgedNode = isBridgedNode)
    }
}
