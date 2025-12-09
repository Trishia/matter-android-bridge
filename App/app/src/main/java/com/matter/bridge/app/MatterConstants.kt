package com.matter.bridge.app

/**
 * Matter Cluster and Attribute ID constants.
 * These IDs are defined by the Matter specification.
 */
object MatterConstants {
    
    object OnOff {
        const val CLUSTER_ID = 0x0006
        
        object Attributes {
            const val ON_OFF = 0x0000
            const val CLUSTER_REVISION = 0xFFFD
        }
        
        object Commands {
            const val OFF = 0x00
            const val ON = 0x01
            const val TOGGLE = 0x02
        }
    }
    
    object LevelControl {
        const val CLUSTER_ID = 0x0008
        
        object Attributes {
            const val CURRENT_LEVEL = 0x0000
            const val CLUSTER_REVISION = 0xFFFD
        }
    }
    
    object TemperatureMeasurement {
        const val CLUSTER_ID = 0x0402
        
        object Attributes {
            const val MEASURED_VALUE = 0x0000
            const val MIN_MEASURED_VALUE = 0x0001
            const val MAX_MEASURED_VALUE = 0x0002
            const val CLUSTER_REVISION = 0xFFFD
        }
    }
    
    object RelativeHumidityMeasurement {
        const val CLUSTER_ID = 0x0405
        
        object Attributes {
            const val MEASURED_VALUE = 0x0000
            const val MIN_MEASURED_VALUE = 0x0001
            const val MAX_MEASURED_VALUE = 0x0002
            const val CLUSTER_REVISION = 0xFFFD
        }
    }
    
    object DoorLock {
        const val CLUSTER_ID = 0x0101
        
        object Attributes {
            const val LOCK_STATE = 0x0000
            const val LOCK_TYPE = 0x0001
            const val CLUSTER_REVISION = 0xFFFD
        }
    }
    
    object BridgedDeviceBasicInformation {
        const val CLUSTER_ID = 0x0039
        
        object Attributes {
            const val NODE_LABEL = 0x0005
            const val REACHABLE = 0x0011
            const val CLUSTER_REVISION = 0xFFFD
        }
    }
    
    object PowerSource {
        const val CLUSTER_ID = 0x002F
        
        object Attributes {
            const val BAT_CHARGE_LEVEL = 0x000E
            const val ORDER = 0x0005
            const val STATUS = 0x0000
            const val DESCRIPTION = 0x0006
            const val CLUSTER_REVISION = 0xFFFD
        }
    }

    object DeviceType {
        const val BRIDGED_NODE = 0x0013
        const val ON_OFF_LIGHT = 0x0100
        const val POWER_SOURCE = 0x0011
        const val TEMP_SENSOR = 0x0302
        const val HUMIDITY_SENSOR = 0x0307
    }

    object AttributeType {
        const val BOOLEAN = 0x10
        const val INT8U = 0x20
        const val INT16S = 0x29
        const val INT16U = 0x21
        const val INT32U = 0x23
        const val ENUM8 = 0x30
        const val CHAR_STRING = 0x42
        const val BITMAP32 = 0x1B
    }

    object AttributeMask {
        const val NONE = 0x00
        const val WRITABLE = 0x01 // ZAP_ATTRIBUTE_MASK(WRITABLE)
        const val EXTERNAL_STORAGE = 0x10 // ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE)
        const val READABLE = 0x20 // ZAP_ATTRIBUTE_MASK(READABLE)
    }
}
