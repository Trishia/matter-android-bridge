package com.matter.bridge.app

import timber.log.Timber

/**
 * Handles server initialization and default device setup.
 * This replaces the hardcoded device initialization that was previously in C++ BridgeApp-JNI.cpp.
 */
object ServerInitializer {
    
    /**
     * Initialize default devices after server startup.
     * This creates the same devices that were previously hardcoded in C++:
     * - 2 Lights (Light 1, Light 2)
     * - 2 Sensors (TemperatureSensor 1, HumiditySensor 1)
     * - 4 Action Lights (Action Light 1-4)
     * - 2 Composed Temperature Sensors
     */
    fun initializeDefaultDevices(devices: MutableList<BridgedDevice>) {
        Timber.d("ServerInitializer: Creating default devices")
        
        try {
            // Create initial bridged devices
            // Create lights (endpoints 2-3)
            devices.add(DeviceFactory.createLight("Light 1", 2))
            devices.add(DeviceFactory.createLight("Light 2", 3))
            // Create Temperature/Humidity Sensor (endpoints 4-5)
            devices.add(DeviceFactory.createTempSensor("Temp Sensor 1", endpoint = 4))
            devices.add(DeviceFactory.createHumiditySensor("Humidity Sensor 1", endpoint = 5))
            
            // Composed device example: Parent + Child
            val parentDevice = DeviceFactory.createComposedDevice("Composed Device", endpoint = 10)
            devices.add(parentDevice)
            
            // Child sensor under composed device (endpoint 11 has parent 10)
            val childTempSensor = DeviceFactory.createComposedTempSensor(
                "Composed Temp Sensor",
                endpoint = 11, 
                parentEndpointId = 10
            )
            devices.add(childTempSensor)
            
            // Child humidity sensor under composed device (endpoint 12 has parent 10)
            val childHumiditySensor = DeviceFactory.createComposedHumiditySensor(
                "Composed Humidity Sensor", 
                endpoint = 12, 
                parentEndpointId = 10
            )
            devices.add(childHumiditySensor)
            
            devices.sortBy { it.endpoint }
            
            Timber.i("Successfully initialized ${devices.size} bridged devices")
        } catch (e: Exception) {
            Timber.e(e, "ServerInitializer: Failed to create default devices")
        }
    }
    
    /**
     * Initialize a minimal set of devices for testing.
     * Creates just 1 light and 1 temp sensor.
     */
    fun initializeMinimalDevices(devices: MutableList<BridgedDevice>) {
        Timber.d("ServerInitializer: Creating minimal device set")
        
        try {
            devices.add(DeviceFactory.createLight("Light 1", 2))
            devices.add(DeviceFactory.createTempSensor("Temperature Sensor 1", 4))
            
            devices.sortBy { it.endpoint }
            
            Timber.i("ServerInitializer: Successfully created minimal device set")
        } catch (e: Exception) {
            Timber.e(e, "ServerInitializer: Failed to create minimal devices")
        }
    }
}
