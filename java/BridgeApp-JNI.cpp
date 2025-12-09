/*
 *   Copyright (c) 2023 Project CHIP Authors
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

#include "AppImpl.h"
#include "JNIDACProvider.h"
#include "BridgeApp-JNI.h"
#include "Device.h"
#include "main.h"

#include <app-common/zap-generated/ids/Attributes.h>
#include <app-common/zap-generated/ids/Clusters.h>
#include <app/AttributeAccessInterfaceRegistry.h>
#include <app/ConcreteAttributePath.h>
#include <app/EventLogging.h>
#include <app/reporting/reporting.h>
#include <app/util/af-types.h>
#include <app/util/attribute-storage.h>
#include <app/util/endpoint-config-api.h>
#include <app/util/util.h>
#include <credentials/DeviceAttestationCredsProvider.h>
#include <credentials/examples/DeviceAttestationCredsExample.h>
#include <lib/core/CHIPError.h>
#include <lib/support/CHIPMem.h>
#include <lib/support/ZclString.h>
#include <platform/CHIPDeviceLayer.h>
#include <app/server/java/AndroidAppServerWrapper.h>
#include <jni.h>
#include <lib/support/CHIPJNIError.h>
#include <lib/support/JniReferences.h>
#include <lib/support/JniTypeWrappers.h>
#include <setup_payload/OnboardingCodesUtil.h>
#include <app/InteractionModelEngine.h>
#include <app/CommandHandlerInterface.h>
#include <app/CommandHandlerInterfaceRegistry.h>


#include <cassert>
#include <iostream>
#include <android/log.h>
#include <string>
#include <vector>

using namespace chip;
using namespace chip::app;
using namespace chip::Credentials;
using namespace chip::Inet;
using namespace chip::Transport;
using namespace chip::DeviceLayer;
using namespace chip::app::Clusters;

// These variables need to be in global scope for bridged-actions-stub.cpp to access them
std::vector<Room *> gRooms;
std::vector<Action *> gActions;

namespace {

const int kNodeLabelSize = 32;
const int kUniqueIdSize  = 32;
#define DEVICE_TYPE_BRIDGED_NODE 0x0013
// Current ZCL implementation of Struct uses a max-size array of 254 bytes
const int kDescriptorAttributeArraySize = 254;

// Declare Descriptor cluster attributes
DECLARE_DYNAMIC_ATTRIBUTE_LIST_BEGIN(descriptorAttrs)
DECLARE_DYNAMIC_ATTRIBUTE(Descriptor::Attributes::DeviceTypeList::Id, ARRAY, kDescriptorAttributeArraySize, 0), /* device list */
    DECLARE_DYNAMIC_ATTRIBUTE(Descriptor::Attributes::ServerList::Id, ARRAY, kDescriptorAttributeArraySize, 0), /* server list */
    DECLARE_DYNAMIC_ATTRIBUTE(Descriptor::Attributes::ClientList::Id, ARRAY, kDescriptorAttributeArraySize, 0), /* client list */
    DECLARE_DYNAMIC_ATTRIBUTE(Descriptor::Attributes::PartsList::Id, ARRAY, kDescriptorAttributeArraySize, 0),  /* parts list */
#if CHIP_CONFIG_USE_ENDPOINT_UNIQUE_ID
    DECLARE_DYNAMIC_ATTRIBUTE(Descriptor::Attributes::EndpointUniqueID::Id, ARRAY, 32, 0), /* endpoint unique id*/
#endif
    DECLARE_DYNAMIC_ATTRIBUTE_LIST_END();

// Declare Bridged Device Basic Information cluster attributes
DECLARE_DYNAMIC_ATTRIBUTE_LIST_BEGIN(bridgedDeviceBasicAttrs)
DECLARE_DYNAMIC_ATTRIBUTE(BridgedDeviceBasicInformation::Attributes::NodeLabel::Id, CHAR_STRING, kNodeLabelSize,
                          ZAP_ATTRIBUTE_MASK(WRITABLE) | ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE)),         /* NodeLabel */
    DECLARE_DYNAMIC_ATTRIBUTE(BridgedDeviceBasicInformation::Attributes::Reachable::Id, BOOLEAN, 1, 0), /* Reachable */
    DECLARE_DYNAMIC_ATTRIBUTE(BridgedDeviceBasicInformation::Attributes::UniqueID::Id, CHAR_STRING, kUniqueIdSize, 0),
    DECLARE_DYNAMIC_ATTRIBUTE(BridgedDeviceBasicInformation::Attributes::ConfigurationVersion::Id, INT32U, 4,
                              0), /* Configuration Version */
    DECLARE_DYNAMIC_ATTRIBUTE(BridgedDeviceBasicInformation::Attributes::FeatureMap::Id, BITMAP32, 4, 0), /* feature map */
    DECLARE_DYNAMIC_ATTRIBUTE_LIST_END();
// Unused constants removed

EndpointId gCurrentEndpointId;
EndpointId gFirstDynamicEndpointId;
// Power source is on the same endpoint as the composed device
Device * gDevices[CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT + 1];

// Unused constants removed

// ENDPOINT DEFINITIONS:
// =================================================================================
//
// Endpoint definitions will be reused across multiple endpoints for every instance of the
// endpoint type.
// There will be no intrinsic storage for the endpoint attributes declared here.
// Instead, all attributes will be treated as EXTERNAL, and therefore all reads
// or writes to the attributes must be handled within the emberAfExternalAttributeWriteCallback
// and emberAfExternalAttributeReadCallback functions declared herein. This fits
// the typical model of a bridge, since a bridge typically maintains its own
// state database representing the devices connected to it.

// Device types for dynamic endpoints: TODO Need a generated file from ZAP to define these!
// (taken from matter-devices.xml)
#define DEVICE_TYPE_BRIDGED_NODE 0x0013
// (taken from lo-devices.xml)
#define DEVICE_TYPE_LO_ON_OFF_LIGHT 0x0100
// (taken from matter-devices.xml)
#define DEVICE_TYPE_POWER_SOURCE 0x0011
// (taken from matter-devices.xml)
#define DEVICE_TYPE_TEMP_SENSOR 0x0302

// Device Version for dynamic endpoints:
#define DEVICE_VERSION_DEFAULT 1

// ---------------------------------------------------------------------------
//
// LIGHT ENDPOINT: contains the following clusters:
//   - On/Off
//   - Descriptor
//   - Bridged Device Basic Information

// Attribute lists removed


// Attribute lists removed


// Attribute lists removed


// Command lists removed


// Cluster lists removed


// Declare Bridged Light endpoint
// Dynamic endpoint declarations removed as they are now handled by Generic device logic


// Attribute lists removed


// ---------------------------------------------------------------------------
//
// TEMPERATURE SENSOR ENDPOINT: contains the following clusters:
//   - Temperature measurement
//   - Descriptor
//   - Bridged Device Basic Information
// Cluster lists removed


// Declare Bridged Light endpoint
// Dynamic endpoint declarations removed


// ---------------------------------------------------------------------------
//
// COMPOSED DEVICE ENDPOINT: contains the following clusters:
//   - Descriptor
//   - Bridged Device Basic Information
//   - Power source

// Attribute lists removed


// Cluster lists removed


// Composed device declarations removed


} // namespace

// REVISION DEFINITIONS:
// =================================================================================

#define ZCL_DESCRIPTOR_CLUSTER_REVISION (1u)
#define ZCL_BRIDGED_DEVICE_BASIC_INFORMATION_CLUSTER_REVISION (2u)
#define ZCL_BRIDGED_DEVICE_BASIC_INFORMATION_FEATURE_MAP (0u)
#define ZCL_FIXED_LABEL_CLUSTER_REVISION (1u)
#define ZCL_ON_OFF_CLUSTER_REVISION (4u)
#define ZCL_TEMPERATURE_SENSOR_CLUSTER_REVISION (1u)
#define ZCL_TEMPERATURE_SENSOR_FEATURE_MAP (0u)
#define ZCL_POWER_SOURCE_CLUSTER_REVISION (2u)

// ---------------------------------------------------------------------------

// Track which devices are dynamically allocated (vs static from postServerInit)
static bool gDynamicDevices[CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT] = {false};

// Device type tracking (since RTTI is disabled)
enum class DeviceType
{
    Unknown,
    OnOff,
    TempSensor,
    OnOffLightSwitch,
    Generic // New generic type
};
static DeviceType gDeviceTypes[CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT] = {DeviceType::Unknown};

// Generic Device Implementation
class DeviceGeneric : public Device
{
public:
    DeviceGeneric(const char * szDeviceName, std::string szLocation) : Device(szDeviceName, szLocation) {}

    void SetAttribute(chip::ClusterId clusterId, chip::AttributeId attributeId, uint64_t value)
    {
        mAttributes[clusterId][attributeId] = value;
    }

    uint64_t GetAttribute(chip::ClusterId clusterId, chip::AttributeId attributeId)
    {
        return mAttributes[clusterId][attributeId];
    }

    bool HasAttribute(chip::ClusterId clusterId, chip::AttributeId attributeId)
    {
        return mAttributes.count(clusterId) && mAttributes[clusterId].count(attributeId);
    }

private:
    void HandleDeviceChange(Device * device, Device::Changed_t changeMask) override {}
    
    std::map<chip::ClusterId, std::map<chip::AttributeId, uint64_t>> mAttributes;
};

// Dynamic Endpoint Memory Management
struct DynamicEndpointData {
    std::vector<EmberAfCluster> clusters;
    std::vector<std::vector<EmberAfAttributeMetadata>> attributes; // Attributes per cluster
    EmberAfEndpointType endpointType;
    DataVersion* dataVersions;
    std::vector<EmberAfDeviceType> deviceTypes;
};

static std::vector<DynamicEndpointData*> gDynamicEndpoints;

// Define accepted command lists for clusters
constexpr CommandId onOffIncomingCommands[] = {
    app::Clusters::OnOff::Commands::Off::Id,
    app::Clusters::OnOff::Commands::On::Id,
    app::Clusters::OnOff::Commands::Toggle::Id,
    kInvalidCommandId,
};

// Helper to create attributes for common clusters
std::vector<EmberAfAttributeMetadata> GetAttributesForCluster(chip::ClusterId clusterId)
{
    std::vector<EmberAfAttributeMetadata> attrs;
    
    if (clusterId == OnOff::Id) {
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), OnOff::Attributes::OnOff::Id, 1, ZAP_TYPE(BOOLEAN), ZAP_ATTRIBUTE_MASK(WRITABLE) | ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
    } else if (clusterId == TemperatureMeasurement::Id) {
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), TemperatureMeasurement::Attributes::MeasuredValue::Id, 2, ZAP_TYPE(INT16S), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), TemperatureMeasurement::Attributes::MinMeasuredValue::Id, 2, ZAP_TYPE(INT16S), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), TemperatureMeasurement::Attributes::MaxMeasuredValue::Id, 2, ZAP_TYPE(INT16S), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
    } else if (clusterId == RelativeHumidityMeasurement::Id) {
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), RelativeHumidityMeasurement::Attributes::MeasuredValue::Id, 2, ZAP_TYPE(INT16U), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), RelativeHumidityMeasurement::Attributes::MinMeasuredValue::Id, 2, ZAP_TYPE(INT16U), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
        attrs.push_back({ ZAP_EMPTY_DEFAULT(), RelativeHumidityMeasurement::Attributes::MaxMeasuredValue::Id, 2, ZAP_TYPE(INT16U), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
    }
    // Add common attributes for all clusters
    attrs.push_back({ ZAP_EMPTY_DEFAULT(), Globals::Attributes::ClusterRevision::Id, 2, ZAP_TYPE(INT16U), ZAP_ATTRIBUTE_MASK(EXTERNAL_STORAGE) });
    
    return attrs;
}


int AddDeviceEndpoint(Device * dev, EmberAfEndpointType * ep, const Span<const EmberAfDeviceType> & deviceTypeList,
                      const Span<DataVersion> & dataVersionStorage,
                      chip::EndpointId requestedEndpointId,
#if CHIP_CONFIG_USE_ENDPOINT_UNIQUE_ID
                      chip::CharSpan epUniqueId,
#endif
                      chip::EndpointId parentEndpointId = chip::kInvalidEndpointId)
{
    uint8_t index = 0;
    while (index < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
    {
        if (nullptr == gDevices[index])
        {
            gDevices[index] = dev;
            CHIP_ERROR err;
            while (true)
            {
                // Todo: Update this to schedule the work rather than use this lock
                // DeviceLayer::StackLock lock; // Removed to avoid deadlock in ScheduleWork
                
                chip::EndpointId endpointToUse = (requestedEndpointId != chip::kInvalidEndpointId) ? requestedEndpointId : gCurrentEndpointId;
                
                dev->SetEndpointId(endpointToUse);
                dev->SetParentEndpointId(parentEndpointId);
#if !CHIP_CONFIG_USE_ENDPOINT_UNIQUE_ID
                err =
                    emberAfSetDynamicEndpoint(index, endpointToUse, ep, dataVersionStorage, deviceTypeList, parentEndpointId);
#else
                err = emberAfSetDynamicEndpointWithEpUniqueId(index, endpointToUse, ep, dataVersionStorage, deviceTypeList,
                                                              epUniqueId, parentEndpointId);
#endif
                if (err == CHIP_NO_ERROR)
                {
                    ChipLogProgress(DeviceLayer, "Added device %s to dynamic endpoint %d (index=%d)", dev->GetName(),
                                    endpointToUse, index);

                    if (dev->GetUniqueId()[0] == '\0')
                    {
                        dev->GenerateUniqueId();
                    }

                    // Only increment gCurrentEndpointId if we used it
                    if (requestedEndpointId == chip::kInvalidEndpointId)
                    {
                        // Handle wrap condition
                        if (++gCurrentEndpointId < gFirstDynamicEndpointId)
                        {
                            gCurrentEndpointId = gFirstDynamicEndpointId;
                        }
                    }

                    return index;
                }
                if (err != CHIP_ERROR_ENDPOINT_EXISTS)
                {
                    gDevices[index] = nullptr;
                    return -1;
                }
                
                // If we requested a specific endpoint and it failed, abort
                if (requestedEndpointId != chip::kInvalidEndpointId)
                {
                    gDevices[index] = nullptr;
                    return -1;
                }

                // Handle wrap condition for auto-assigned endpoints
                if (++gCurrentEndpointId < gFirstDynamicEndpointId)
                {
                    gCurrentEndpointId = gFirstDynamicEndpointId;
                }
            }
        }
        index++;
    }
    ChipLogProgress(DeviceLayer, "Failed to add dynamic endpoint: No endpoints available!");
    return -1;
}

int RemoveDeviceEndpoint(Device * dev)
{
    uint8_t index = 0;
    while (index < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
    {
        if (gDevices[index] == dev)
        {
            // Todo: Update this to schedule the work rather than use this lock
            // DeviceLayer::StackLock lock; // Removed to avoid deadlock in ScheduleWork
            // Silence complaints about unused ep when progress logging
            // disabled.
            [[maybe_unused]] EndpointId ep = emberAfClearDynamicEndpoint(index);
            gDevices[index]                = nullptr;
            ChipLogProgress(DeviceLayer, "Removed device %s from dynamic endpoint %d (index=%d)", dev->GetName(), ep, index);
            return index;
        }
        index++;
    }
    return -1;
}

std::vector<EndpointListInfo> GetEndpointListInfo(chip::EndpointId parentId)
{
    std::vector<EndpointListInfo> infoList;

    for (auto room : gRooms)
    {
        if (room->getIsVisible())
        {
            EndpointListInfo info(room->getEndpointListId(), room->getName(), room->getType());
            int index = 0;
            while (index < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
            {
                if ((gDevices[index] != nullptr) && (gDevices[index]->GetParentEndpointId() == parentId))
                {
                    std::string location;
                    if (room->getType() == Actions::EndpointListTypeEnum::kZone)
                    {
                        location = gDevices[index]->GetZone();
                    }
                    else
                    {
                        location = gDevices[index]->GetLocation();
                    }
                    if (room->getName().compare(location) == 0)
                    {
                        info.AddEndpointId(gDevices[index]->GetEndpointId());
                    }
                }
                index++;
            }
            if (info.GetEndpointListSize() > 0)
            {
                infoList.push_back(info);
            }
        }
    }

    return infoList;
}

std::vector<Action *> GetActionListInfo(chip::EndpointId parentId)
{
    return gActions;
}

std::vector<Room *> GetRoomListInfo(chip::EndpointId parentId)
{
    return gRooms;
}

namespace {
void CallReportingCallback(intptr_t closure)
{
    auto path = reinterpret_cast<app::ConcreteAttributePath *>(closure);
    MatterReportingAttributeChangeCallback(*path);
    Platform::Delete(path);
}

void ScheduleReportingCallback(Device * dev, ClusterId cluster, AttributeId attribute)
{
    auto * path = Platform::New<app::ConcreteAttributePath>(dev->GetEndpointId(), cluster, attribute);
    PlatformMgr().ScheduleWork(CallReportingCallback, reinterpret_cast<intptr_t>(path));
}
} // anonymous namespace

void HandleDeviceStatusChanged(Device * dev, Device::Changed_t itemChangedMask)
{
    if (itemChangedMask & Device::kChanged_Reachable)
    {
        ScheduleReportingCallback(dev, BridgedDeviceBasicInformation::Id, BridgedDeviceBasicInformation::Attributes::Reachable::Id);
    }

    if (itemChangedMask & Device::kChanged_Name)
    {
        ScheduleReportingCallback(dev, BridgedDeviceBasicInformation::Id, BridgedDeviceBasicInformation::Attributes::NodeLabel::Id);
    }
}

void HandleDeviceOnOffStatusChanged(DeviceOnOff * dev, DeviceOnOff::Changed_t itemChangedMask)
{
    if (itemChangedMask & (DeviceOnOff::kChanged_Reachable | DeviceOnOff::kChanged_Name | DeviceOnOff::kChanged_Location))
    {
        HandleDeviceStatusChanged(static_cast<Device *>(dev), (Device::Changed_t) itemChangedMask);
    }

    if (itemChangedMask & DeviceOnOff::kChanged_OnOff)
    {
        ScheduleReportingCallback(dev, OnOff::Id, OnOff::Attributes::OnOff::Id);
    }
}

void HandleDevicePowerSourceStatusChanged(DevicePowerSource * dev, DevicePowerSource::Changed_t itemChangedMask)
{
    using namespace app::Clusters;
    if (itemChangedMask &
        (DevicePowerSource::kChanged_Reachable | DevicePowerSource::kChanged_Name | DevicePowerSource::kChanged_Location))
    {
        HandleDeviceStatusChanged(static_cast<Device *>(dev), (Device::Changed_t) itemChangedMask);
    }

    if (itemChangedMask & DevicePowerSource::kChanged_BatLevel)
    {
        MatterReportingAttributeChangeCallback(dev->GetEndpointId(), PowerSource::Id, PowerSource::Attributes::BatChargeLevel::Id);
    }

    if (itemChangedMask & DevicePowerSource::kChanged_Description)
    {
        MatterReportingAttributeChangeCallback(dev->GetEndpointId(), PowerSource::Id, PowerSource::Attributes::Description::Id);
    }
    if (itemChangedMask & DevicePowerSource::kChanged_EndpointList)
    {
        MatterReportingAttributeChangeCallback(dev->GetEndpointId(), PowerSource::Id, PowerSource::Attributes::EndpointList::Id);
    }
}

void HandleDeviceTempSensorStatusChanged(DeviceTempSensor * dev, DeviceTempSensor::Changed_t itemChangedMask)
{
    if (itemChangedMask &
        (DeviceTempSensor::kChanged_Reachable | DeviceTempSensor::kChanged_Name | DeviceTempSensor::kChanged_Location))
    {
        HandleDeviceStatusChanged(static_cast<Device *>(dev), (Device::Changed_t) itemChangedMask);
    }
    if (itemChangedMask & DeviceTempSensor::kChanged_MeasurementValue)
    {
        ScheduleReportingCallback(dev, TemperatureMeasurement::Id, TemperatureMeasurement::Attributes::MeasuredValue::Id);
    }
}

Protocols::InteractionModel::Status HandleReadBridgedDeviceBasicAttribute(Device * dev, chip::AttributeId attributeId,
                                                                          uint8_t * buffer, uint16_t maxReadLength)
{
    using namespace BridgedDeviceBasicInformation::Attributes;

    ChipLogProgress(DeviceLayer, "HandleReadBridgedDeviceBasicAttribute: attrId=%d, maxReadLength=%d", attributeId, maxReadLength);

    if ((attributeId == BridgedDeviceBasicInformation::Attributes::Reachable::Id) && (maxReadLength >= 1))
    {
        // Always return true for reachable (device is online)
        // TODO: Integrate with actual reachability tracking from Java/Kotlin layer
        *buffer = dev->IsReachable() ? 1 : 0;
    }
    else if ((attributeId == BridgedDeviceBasicInformation::Attributes::NodeLabel::Id) && (maxReadLength >= 32))
    {
        MutableByteSpan zclNameSpan(buffer, maxReadLength);
        MakeZclCharString(zclNameSpan, dev->GetName());
    }
    else if ((attributeId == BridgedDeviceBasicInformation::Attributes::UniqueID::Id) && (maxReadLength >= 32))
    {
        MutableByteSpan zclUniqueIdSpan(buffer, maxReadLength);
        MakeZclCharString(zclUniqueIdSpan, dev->GetUniqueId());
    }
    else if ((attributeId == BridgedDeviceBasicInformation::Attributes::ConfigurationVersion::Id) && (maxReadLength >= 4))
    {
        uint32_t configVersion = dev->GetConfigurationVersion();
        memcpy(buffer, &configVersion, sizeof(configVersion));
    }
    else if ((attributeId == BridgedDeviceBasicInformation::Attributes::ClusterRevision::Id) && (maxReadLength >= 2))
    {
        uint16_t rev = ZCL_BRIDGED_DEVICE_BASIC_INFORMATION_CLUSTER_REVISION;
        memcpy(buffer, &rev, sizeof(rev));
    }
    else if ((attributeId == BridgedDeviceBasicInformation::Attributes::FeatureMap::Id) && (maxReadLength >= 4))
    {
        uint32_t featureMap = ZCL_BRIDGED_DEVICE_BASIC_INFORMATION_FEATURE_MAP;
        memcpy(buffer, &featureMap, sizeof(featureMap));
    }
    else
    {
        return Protocols::InteractionModel::Status::Failure;
    }

    return Protocols::InteractionModel::Status::Success;
}


Protocols::InteractionModel::Status HandleWriteBridgedDeviceBasicAttribute(Device * dev, AttributeId attributeId, uint8_t * buffer)
{
    ChipLogProgress(DeviceLayer, "HandleWriteBridgedDeviceBasicAttribute: attrId=" ChipLogFormatMEI, ChipLogValueMEI(attributeId));

    if (attributeId != BridgedDeviceBasicInformation::Attributes::NodeLabel::Id)
    {
        return Protocols::InteractionModel::Status::UnsupportedWrite;
    }

    CharSpan nameSpan = CharSpan::fromZclString(buffer);

    if (nameSpan.size() > kNodeLabelSize)
    {
        return Protocols::InteractionModel::Status::ConstraintError;
    }

    std::string name(nameSpan.data(), nameSpan.size());
    dev->SetName(name.c_str());

    HandleDeviceStatusChanged(dev, Device::kChanged_Name);
    
    // Notify Java layer of state change
    uint64_t hashValue = std::hash<std::string>{}(name);
    BridgeAppJNIMgr().PostDeviceStateChanged(dev->GetEndpointId(), BridgedDeviceBasicInformation::Id, 
                                            static_cast<int>(attributeId), reinterpret_cast<uint8_t*>(&hashValue), sizeof(hashValue));

    return Protocols::InteractionModel::Status::Success;
}


Protocols::InteractionModel::Status emberAfExternalAttributeReadCallback(EndpointId endpoint, ClusterId clusterId,
                                                                         const EmberAfAttributeMetadata * attributeMetadata,
                                                                         uint8_t * buffer, uint16_t maxReadLength)
{
    uint16_t endpointIndex = emberAfGetDynamicIndexFromEndpoint(endpoint);

    Protocols::InteractionModel::Status ret = Protocols::InteractionModel::Status::Failure;

    if ((endpointIndex < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT) && (gDevices[endpointIndex] != nullptr))
    {
        Device * dev = gDevices[endpointIndex];

        // Handle BridgedDeviceBasicInformation in C++ (infrastructure cluster)
        if (clusterId == BridgedDeviceBasicInformation::Id)
        {
            ret = HandleReadBridgedDeviceBasicAttribute(dev, attributeMetadata->attributeId, buffer, maxReadLength);
        }
        else
        {
            // Forward all other clusters to Java/Kotlin layer for handling
            auto result = BridgeAppJNIMgr().HandleClusterAttributeRead(endpoint, static_cast<int>(clusterId), static_cast<int>(attributeMetadata->attributeId), maxReadLength);
            
            if (result.data() != nullptr && result.size() > 0 && result.size() <= maxReadLength)
            {
                memcpy(buffer, result.data(), static_cast<size_t>(result.size()));
                ret = Protocols::InteractionModel::Status::Success;
            }
            else
            {
                ret = Protocols::InteractionModel::Status::UnsupportedAttribute;
            }
        }
    }
    // Special handling for Aggregator endpoint (Endpoint 1) - Descriptor::PartsList
    else if (endpoint == 1 && clusterId == Descriptor::Id && 
             attributeMetadata->attributeId == Descriptor::Attributes::PartsList::Id)
    {
        // Note: This is a simplified implementation
        // In production, use AttributeAccessInterface with proper TLV encoding
        // For now, return empty list - full implementation requires TLV encoder
        ret = Protocols::InteractionModel::Status::UnsupportedAttribute;
        ChipLogProgress(DeviceLayer, "PartsList read requested on Aggregator - needs full TLV implementation");
    }

    return ret;
}

class BridgedPowerSourceAttrAccess : public AttributeAccessInterface
{
public:
    // Register on all endpoints.
    BridgedPowerSourceAttrAccess() : AttributeAccessInterface(Optional<EndpointId>::Missing(), PowerSource::Id) {}

    CHIP_ERROR
    Read(const ConcreteReadAttributePath & aPath, AttributeValueEncoder & aEncoder) override
    {
        uint16_t powerSourceDeviceIndex = CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT;

        if ((gDevices[powerSourceDeviceIndex] != nullptr))
        {
            DevicePowerSource * dev = static_cast<DevicePowerSource *>(gDevices[powerSourceDeviceIndex]);
            if (aPath.mEndpointId != dev->GetEndpointId())
            {
                return CHIP_IM_GLOBAL_STATUS(UnsupportedEndpoint);
            }
            switch (aPath.mAttributeId)
            {
            case PowerSource::Attributes::BatChargeLevel::Id:
                aEncoder.Encode(dev->GetBatChargeLevel());
                break;
            case PowerSource::Attributes::Order::Id:
                aEncoder.Encode(dev->GetOrder());
                break;
            case PowerSource::Attributes::Status::Id:
                aEncoder.Encode(dev->GetStatus());
                break;
            case PowerSource::Attributes::Description::Id:
                aEncoder.Encode(chip::CharSpan(dev->GetDescription().c_str(), dev->GetDescription().size()));
                break;
            case PowerSource::Attributes::EndpointList::Id: {
                std::vector<chip::EndpointId> & list = dev->GetEndpointList();
                DataModel::List<EndpointId> dm_list(chip::Span<chip::EndpointId>(list.data(), list.size()));
                aEncoder.Encode(dm_list);
                break;
            }
            case PowerSource::Attributes::ClusterRevision::Id:
                aEncoder.Encode(ZCL_POWER_SOURCE_CLUSTER_REVISION);
                break;
            case PowerSource::Attributes::FeatureMap::Id:
                aEncoder.Encode(dev->GetFeatureMap());
                break;

            case PowerSource::Attributes::BatReplacementNeeded::Id:
                aEncoder.Encode(false);
                break;
            case PowerSource::Attributes::BatReplaceability::Id:
                aEncoder.Encode(PowerSource::BatReplaceabilityEnum::kNotReplaceable);
                break;
            default:
                return CHIP_IM_GLOBAL_STATUS(UnsupportedAttribute);
            }
        }
        return CHIP_NO_ERROR;
    }
};

BridgedPowerSourceAttrAccess gPowerAttrAccess;

Protocols::InteractionModel::Status emberAfExternalAttributeWriteCallback(EndpointId endpoint, ClusterId clusterId,
                                                                          const EmberAfAttributeMetadata * attributeMetadata,
                                                                          uint8_t * buffer)
{
    uint16_t endpointIndex = emberAfGetDynamicIndexFromEndpoint(endpoint);

    Protocols::InteractionModel::Status ret = Protocols::InteractionModel::Status::Failure;

    if (endpointIndex < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
    {
        Device * dev = gDevices[endpointIndex];

        if (!dev || !dev->IsReachable())
        {
            return Protocols::InteractionModel::Status::Failure;
        }

        // Handle BridgedDeviceBasicInformation in C++ (infrastructure cluster)
        if (clusterId == BridgedDeviceBasicInformation::Id)
        {
            ret = HandleWriteBridgedDeviceBasicAttribute(dev, attributeMetadata->attributeId, buffer);
        }
        else
        {
            // Forward all other clusters to Java/Kotlin layer for handling
            ChipLogProgress(DeviceLayer, "HandleClusterAttributeWrite: Forwarding to Java - ep=%d, cluster=0x%x, attr=0x%x",
                          endpoint, clusterId, attributeMetadata->attributeId);
            
            // Determine buffer size - use reasonable max for most attribute types
            // For more complex types, this should be derived from attribute metadata
            size_t bufferSize = 8; // Most attributes are <= 8 bytes (bool, int8-64, etc.)
            
            bool handled = BridgeAppJNIMgr().HandleClusterAttributeWrite(endpoint, static_cast<int>(clusterId), static_cast<int>(attributeMetadata->attributeId), buffer, bufferSize);
            
            if (handled)
            {
                ChipLogProgress(DeviceLayer, "HandleClusterAttributeWrite: Java handled write successfully");
                
                // Report attribute change to subscribed controllers
                MatterReportingAttributeChangeCallback(endpoint, clusterId, attributeMetadata->attributeId);
                
                // Trigger state change callback for UI updates with the actual buffer data
                BridgeAppJNIMgr().PostDeviceStateChanged(endpoint, static_cast<int>(clusterId), static_cast<int>(attributeMetadata->attributeId), buffer, bufferSize);
                
                ret = Protocols::InteractionModel::Status::Success;
            }
            else
            {
                ChipLogProgress(DeviceLayer, "HandleClusterAttributeWrite: Java did not handle write request");
                ret = Protocols::InteractionModel::Status::UnsupportedAttribute;
            }
        }
    }

    return ret;
}

// Actions and Rooms functionality removed


[[maybe_unused]] const EmberAfDeviceType gBridgedOnOffDeviceTypes[] = { { DEVICE_TYPE_LO_ON_OFF_LIGHT, DEVICE_VERSION_DEFAULT },
                                                       { DEVICE_TYPE_BRIDGED_NODE, DEVICE_VERSION_DEFAULT } };

[[maybe_unused]] const EmberAfDeviceType gBridgedComposedDeviceTypes[] = { { DEVICE_TYPE_BRIDGED_NODE, DEVICE_VERSION_DEFAULT },
                                                          { DEVICE_TYPE_POWER_SOURCE, DEVICE_VERSION_DEFAULT } };

[[maybe_unused]] const EmberAfDeviceType gComposedTempSensorDeviceTypes[] = { { DEVICE_TYPE_TEMP_SENSOR, DEVICE_VERSION_DEFAULT } };

[[maybe_unused]] const EmberAfDeviceType gBridgedTempSensorDeviceTypes[] = { { DEVICE_TYPE_TEMP_SENSOR, DEVICE_VERSION_DEFAULT },
                                                            { DEVICE_TYPE_BRIDGED_NODE, DEVICE_VERSION_DEFAULT } };

#define JNI_METHOD(RETURN, METHOD_NAME)                                                                                            \
    extern "C" JNIEXPORT RETURN JNICALL Java_com_matter_bridge_app_BridgeApp_##METHOD_NAME

#define DEVICE_VERSION_DEFAULT 1

EmberAfDeviceType gDeviceTypeIds[] = { { 0, DEVICE_VERSION_DEFAULT } };
BridgeAppJNI BridgeAppJNI::sInstance;

void BridgeAppJNI::InitializeWithObjects(jobject app)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturn(env != nullptr, ChipLogError(Zcl, "Failed to GetEnvForCurrentThread for BridgeAppJNI"));

    VerifyOrReturn(mDeviceAppObject.Init(app) == CHIP_NO_ERROR, ChipLogError(Zcl, "Failed to init mDeviceAppObject"));

    jclass managerClass = env->GetObjectClass(app);
    VerifyOrReturn(managerClass != nullptr, ChipLogError(Zcl, "Failed to get BridgeAppJNI Java class"));

    mPostClusterInitMethod = env->GetMethodID(managerClass, "postClusterInit", "(JI)V");
    if (mPostClusterInitMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'postClusterInit' method");
        env->ExceptionClear();
    }

    mPostEventMethod = env->GetMethodID(managerClass, "postEvent", "(J)V");
    if (mPostEventMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'postEvent' method");
        env->ExceptionClear();
    }

    mPostDeviceStateChangedMethod = env->GetMethodID(managerClass, "postDeviceStateChanged", "(III[B)V");
    if (mPostDeviceStateChangedMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'postDeviceStateChanged' method");
        env->ExceptionClear();
    }

    mOnAttributeReadMethod = env->GetMethodID(managerClass, "onClusterAttributeReadRequest", "(IIII)[B");
    if (mOnAttributeReadMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'onClusterAttributeReadRequest' method");
        env->ExceptionClear();
    }

    mOnAttributeWriteMethod = env->GetMethodID(managerClass, "onClusterAttributeWriteRequest", "(III[B)Z");
    if (mOnAttributeWriteMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'onClusterAttributeWriteRequest' method");
        env->ExceptionClear();
    }

    mOnCommandMethod = env->GetMethodID(managerClass, "onClusterCommandRequest", "(III)Z");
    if (mOnCommandMethod == nullptr)
    {
        ChipLogError(Zcl, "Failed to access BridgeApp 'onClusterCommandRequest' method");
        env->ExceptionClear();
    }
}

void BridgeAppJNI::PostClusterInit(int clusterId, int endpoint)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturn(env != nullptr, ChipLogError(Zcl, "Failed to GetEnvForCurrentThread for BridgeAppJNI::PostClusterInit"));
    VerifyOrReturn(mDeviceAppObject.HasValidObjectRef(), ChipLogError(Zcl, "BridgeAppJNI::mDeviceAppObject null"));
    VerifyOrReturn(mPostClusterInitMethod != nullptr, ChipLogError(Zcl, "BridgeAppJNI::mPostClusterInitMethod null"));

    env->CallVoidMethod(mDeviceAppObject.ObjectRef(), mPostClusterInitMethod, static_cast<jlong>(clusterId),
                        static_cast<jint>(endpoint));
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "Failed to call BridgeAppJNI 'postClusterInit' method");
        env->ExceptionClear();
    }
}

void BridgeAppJNI::PostEvent(int event)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturn(env != nullptr, ChipLogError(Zcl, "Failed to GetEnvForCurrentThread for BridgeAppJNI::PostEvent"));
    VerifyOrReturn(mDeviceAppObject.HasValidObjectRef(), ChipLogError(Zcl, "BridgeAppJNI::mDeviceAppObject null"));
    VerifyOrReturn(mPostEventMethod != nullptr, ChipLogError(Zcl, "BridgeAppJNI::mPostEventMethod null"));

    env->CallVoidMethod(mDeviceAppObject.ObjectRef(), mPostEventMethod, static_cast<jlong>(event));
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "Failed to call BridgeAppJNI 'postEventMethod' method");
        env->ExceptionClear();
    }
}

void BridgeAppJNI::PostDeviceStateChanged(int endpoint, int clusterId, int attributeId, uint8_t* value, size_t valueSize)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturn(env != nullptr, ChipLogError(Zcl, "PostDeviceStateChanged: Failed to GetEnvForCurrentThread"));
    VerifyOrReturn(mDeviceAppObject.HasValidObjectRef(), ChipLogError(Zcl, "PostDeviceStateChanged: mDeviceAppObject null"));
    VerifyOrReturn(mPostDeviceStateChangedMethod != nullptr, ChipLogError(Zcl, "PostDeviceStateChanged: mPostDeviceStateChangedMethod null"));

    // Convert buffer to Java byte array
    jbyteArray javaValue = nullptr;
    if (value != nullptr && valueSize > 0)
    {
        javaValue = env->NewByteArray(static_cast<jsize>(valueSize));
        if (javaValue != nullptr)
        {
            env->SetByteArrayRegion(javaValue, 0, static_cast<jsize>(valueSize), reinterpret_cast<const jbyte*>(value));
        }
        else
        {
            ChipLogError(Zcl, "PostDeviceStateChanged: Failed to create Java byte array");
            return;
        }
    }

    env->CallVoidMethod(mDeviceAppObject.ObjectRef(), mPostDeviceStateChangedMethod, 
                       static_cast<jint>(endpoint), static_cast<jint>(clusterId), 
                       static_cast<jint>(attributeId), javaValue);
    
    if (javaValue != nullptr)
    {
        env->DeleteLocalRef(javaValue);
    }
    
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "PostDeviceStateChanged: Failed to call 'postDeviceStateChanged' method");
        env->ExceptionClear();
    }
}

chip::JniByteArray BridgeAppJNI::HandleClusterAttributeRead(int endpoint, int clusterId, int attributeId, int maxReadLength)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    
    // Create an empty JniByteArray for error returns
    // We'll only create a real JniByteArray if Java returns a valid result
    if (env == nullptr)
    {
        ChipLogError(Zcl, "HandleClusterAttributeRead: Failed to GetEnvForCurrentThread");
        return chip::JniByteArray(env, nullptr);
    }
    
    if (!mDeviceAppObject.HasValidObjectRef())
    {
        ChipLogError(Zcl, "HandleClusterAttributeRead: mDeviceAppObject null");
        return chip::JniByteArray(env, nullptr);
    }
    
    if (mOnAttributeReadMethod == nullptr)
    {
        ChipLogError(Zcl, "HandleClusterAttributeRead: mOnAttributeReadMethod null");
        return chip::JniByteArray(env, nullptr);
    }

    jbyteArray javaResult = (jbyteArray) env->CallObjectMethod(
        mDeviceAppObject.ObjectRef(), mOnAttributeReadMethod,
        static_cast<jint>(endpoint), static_cast<jint>(clusterId),
        static_cast<jint>(attributeId), static_cast<jint>(maxReadLength));
    
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "HandleClusterAttributeRead: Exception calling onClusterAttributeReadRequest");
        env->ExceptionClear();
        return chip::JniByteArray(env, nullptr);
    }
    
    // Only create JniByteArray if Java returned a valid result
    if (javaResult != nullptr)
    {
        return chip::JniByteArray(env, javaResult);
    }
    else
    {
        return chip::JniByteArray(env, nullptr);
    }
}

bool BridgeAppJNI::HandleClusterAttributeWrite(int endpoint, int clusterId, int attributeId, uint8_t* buffer, size_t bufferSize)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturnValue(env != nullptr, false, ChipLogError(Zcl, "HandleClusterAttributeWrite: Failed to GetEnvForCurrentThread"));
    VerifyOrReturnValue(mDeviceAppObject.HasValidObjectRef(), false, ChipLogError(Zcl, "HandleClusterAttributeWrite: mDeviceAppObject null"));
    VerifyOrReturnValue(mOnAttributeWriteMethod != nullptr, false, ChipLogError(Zcl, "HandleClusterAttributeWrite: mOnAttributeWriteMethod null"));

    // Convert buffer to Java byte array
    jbyteArray javaBuffer = env->NewByteArray(static_cast<jsize>(bufferSize));
    if (javaBuffer == nullptr)
    {
        ChipLogError(Zcl, "HandleClusterAttributeWrite: Failed to create Java byte array");
        return false;
    }
    
    env->SetByteArrayRegion(javaBuffer, 0, static_cast<jsize>(bufferSize), reinterpret_cast<const jbyte*>(buffer));
    
    jboolean result = env->CallBooleanMethod(
        mDeviceAppObject.ObjectRef(), mOnAttributeWriteMethod,
        static_cast<jint>(endpoint), static_cast<jint>(clusterId),
        static_cast<jint>(attributeId), javaBuffer);
    
    env->DeleteLocalRef(javaBuffer);
    
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "HandleClusterAttributeWrite: Exception calling onClusterAttributeWriteRequest");
        env->ExceptionClear();
        return false;
    }
    
    return result == JNI_TRUE;
}

bool BridgeAppJNI::HandleCommand(int endpoint, int clusterId, int commandId)
{
    JNIEnv * env = JniReferences::GetInstance().GetEnvForCurrentThread();
    VerifyOrReturnValue(env != nullptr, false, ChipLogError(Zcl, "HandleCommand: Failed to GetEnvForCurrentThread"));
    VerifyOrReturnValue(mDeviceAppObject.HasValidObjectRef(), false, ChipLogError(Zcl, "HandleCommand: mDeviceAppObject null"));
    VerifyOrReturnValue(mOnCommandMethod != nullptr, false, ChipLogError(Zcl, "HandleCommand: mOnCommandMethod null"));

    jboolean result = env->CallBooleanMethod(
        mDeviceAppObject.ObjectRef(), mOnCommandMethod,
        static_cast<jint>(endpoint), static_cast<jint>(clusterId),
        static_cast<jint>(commandId));
    
    if (env->ExceptionCheck())
    {
        ChipLogError(Zcl, "HandleCommand: Exception calling onClusterCommandRequest");
        env->ExceptionClear();
        return false;
    }
    
    return result == JNI_TRUE;
}

void BridgeAppJNI::ReportAttributeChange(int endpoint, int clusterId, int attributeId)
{
    // Acquire stack lock for thread-safe access to Matter stack
    chip::DeviceLayer::StackLock lock;
    
    MatterReportingAttributeChangeCallback(
        static_cast<chip::EndpointId>(endpoint),
        static_cast<chip::ClusterId>(clusterId),
        static_cast<chip::AttributeId>(attributeId)
    );
}

namespace {

class BridgeDeviceCommandHandler : public chip::app::CommandHandlerInterface
{
public:
    BridgeDeviceCommandHandler() :
        CommandHandlerInterface(Optional<EndpointId>::Missing(), OnOff::Id)
    {}

    void InvokeCommand(HandlerContext & handlerContext) override
    {
        const ConcreteCommandPath & commandPath = handlerContext.mRequestPath;
        
        // Signal that we are handling this command
        handlerContext.SetCommandHandled();
        
        // Forward to Java/Kotlin
        bool handled = BridgeAppJNIMgr().HandleCommand(
            static_cast<int>(commandPath.mEndpointId),
            static_cast<int>(commandPath.mClusterId),
            static_cast<int>(commandPath.mCommandId)
        );
        
        if (handled)
        {
            handlerContext.mCommandHandler.AddStatus(commandPath, Protocols::InteractionModel::Status::Success);
            
            // Report OnOff attribute change after successful command execution
            if (commandPath.mClusterId == OnOff::Id)
            {
                MatterReportingAttributeChangeCallback(
                    commandPath.mEndpointId,
                    OnOff::Id,
                    OnOff::Attributes::OnOff::Id
                );
            }
        }
        else
        {
            handlerContext.mCommandHandler.AddStatus(commandPath, Protocols::InteractionModel::Status::UnsupportedCommand);
        }
    }
};

BridgeDeviceCommandHandler gBridgeDeviceCommandHandler;

} // namespace

jint JNI_OnLoad(JavaVM * jvm, void * reserved)
{
    return AndroidAppServerJNI_OnLoad(jvm, reserved);
}

void JNI_OnUnload(JavaVM * jvm, void * reserved)
{
    return AndroidAppServerJNI_OnUnload(jvm, reserved);
}

JNI_METHOD(void, nativeInit)(JNIEnv *, jobject app)
{
    ChipLogProgress(Zcl, "nativeInit() called");
    BridgeAppJNIMgr().InitializeWithObjects(app);
    ChipLogProgress(Zcl, "nativeInit() completed");
}

// called before Matter server is initiated
JNI_METHOD(void, preServerInit)(JNIEnv *, jobject)
{
    ChipLogProgress(Zcl, "preServerInit() called - minimal C++ initialization");
    // All device initialization now happens in Kotlin
    // C++ only tracks endpoints
}

// called after Matter server is initiated
JNI_METHOD(void, postServerInit)(JNIEnv *, jobject, jint deviceTypeId)
{
    ChipLogProgress(Zcl, "DEBUG_MARKER_UNIQUE_ID_12345: postServerInit() called - initializing endpoint tracking");
    
    // Register command handler
    chip::app::CommandHandlerInterfaceRegistry::Instance().RegisterCommandHandler(&gBridgeDeviceCommandHandler);
    
    // Use ScheduleWork to run on the Matter thread
    ChipLogProgress(Zcl, "postServerInit() calling ScheduleWork");
    CHIP_ERROR err = chip::DeviceLayer::PlatformMgr().ScheduleWork(
        [](intptr_t arg) {
            // Initialize endpoint tracking
            gFirstDynamicEndpointId = static_cast<chip::EndpointId>(
                static_cast<int>(emberAfEndpointFromIndex(static_cast<uint16_t>(emberAfFixedEndpointCount() - 1))) + 1);
            gCurrentEndpointId = gFirstDynamicEndpointId;
            
            ChipLogProgress(Zcl, "postServerInit() completed - first dynamic endpoint ID: %d", gFirstDynamicEndpointId);
        },
        0);
    ChipLogProgress(Zcl, "postServerInit() ScheduleWork returned");
    
    if (err != CHIP_NO_ERROR)
    {
        ChipLogError(Zcl, "Failed to schedule postServerInit work: %" CHIP_ERROR_FORMAT, err.Format());
    }
    else
    {
        ChipLogProgress(Zcl, "Successfully scheduled postServerInit work");
    }
}

JNI_METHOD(void, setDACProvider)(JNIEnv *, jobject, jobject provider)
{
    if (!chip::Credentials::IsDeviceAttestationCredentialsProviderSet())
    {
        chip::Credentials::SetDeviceAttestationCredentialsProvider(chip::Credentials::Examples::GetExampleDACProvider());
    }
}



JNI_METHOD(jstring, getCommissioningQRCode)(JNIEnv * env, jobject)
{
    ChipLogProgress(Zcl, "getCommissioningQRCode");
    chip::DeviceLayer::StackLock lock; // Acquire lock for Matter stack access
    
    char qrCodeBuffer[256];
    chip::MutableCharSpan qrCodeSpan(qrCodeBuffer);
    
    if (GetQRCode(qrCodeSpan, chip::RendezvousInformationFlags(chip::RendezvousInformationFlag::kBLE)) != CHIP_NO_ERROR)
    {
        ChipLogError(Zcl, "Failed to get QR code");
        return env->NewStringUTF("");
    }
    
    return env->NewStringUTF(qrCodeBuffer);
}

// Structure to pass data to ScheduleWork callback
struct AddDeviceContext {
    Device * device;
    EmberAfEndpointType * epType;
    Span<const EmberAfDeviceType> deviceTypes;
    Span<DataVersion> dataVersions;
    int type; // Added type member
};

JNI_METHOD(void, reportAttributeChange)(JNIEnv *, jobject, jint endpoint, jint clusterId, jint attributeId)
{
    BridgeAppJNIMgr().ReportAttributeChange(endpoint, clusterId, attributeId);
}

JNI_METHOD(jboolean, removeBridgedDevice)(JNIEnv *, jobject, jint endpoint)
{
    ChipLogProgress(Zcl, "removeBridgedDevice: endpoint=%d", endpoint);
    
    chip::DeviceLayer::StackLock lock;  // Thread-safe access to gDevices
    
    // Find the device with matching endpoint ID
    // We need to search through gDevices because endpoint ID != array index
    Device * deviceToRemove = nullptr;
    int deviceIndex = -1;
    
    for (int i = 0; i < CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT; i++)
    {
        if (gDevices[i] != nullptr && gDevices[i]->GetEndpointId() == static_cast<EndpointId>(endpoint))
        {
            deviceToRemove = gDevices[i];
            deviceIndex = i;
            ChipLogProgress(Zcl, "Found device '%s' at endpoint %d, index %d", 
                           gDevices[i]->GetName(), endpoint, i);
            break;
        }
    }
    
    if (deviceToRemove == nullptr)
    {
        ChipLogError(Zcl, "Device not found with endpoint %d", endpoint);
        return JNI_FALSE;
    }
    
    // Create context for ScheduleWork
    struct RemoveDeviceContext {
        Device * device;
        int index;
    };
    
    RemoveDeviceContext * context = new RemoveDeviceContext{deviceToRemove, deviceIndex};
    
    // Schedule work on Matter thread to avoid threading issues
    chip::DeviceLayer::PlatformMgr().ScheduleWork(
        [](intptr_t arg) {
            RemoveDeviceContext * ctx = reinterpret_cast<RemoveDeviceContext*>(arg);
            
            int ret = RemoveDeviceEndpoint(ctx->device);
            
            if (ret >= 0)  // RemoveDeviceEndpoint returns index on success, -1 on failure
            {
                ChipLogProgress(Zcl, "Successfully removed device at index %d", ret);
                // RemoveDeviceEndpoint already set gDevices[ret] = nullptr
                
                // Clear device type
                gDeviceTypes[ret] = DeviceType::Unknown;
                
                // Only delete if this was a dynamically allocated device
                if (gDynamicDevices[ret])
                {
                    delete ctx->device;
                    gDynamicDevices[ret] = false;
                }
            }
            else
            {
                ChipLogError(Zcl, "Failed to remove device");
            }
            
            delete ctx;
        }, reinterpret_cast<intptr_t>(context));
    
    // Return true immediately - actual operation happens asynchronously
    return JNI_TRUE;
}

#define DEVICE_VERSION_DEFAULT 1

// Generic Device Support JNI Methods
JNI_METHOD(jboolean, addBridgedDevice)(JNIEnv * env, jobject, jint endpoint, jint parentEndpointId, jstring name, jintArray clusterIds, jobjectArray attributes, jintArray deviceTypeIds)
{
    const char * deviceName = env->GetStringUTFChars(name, nullptr);
    jsize clusterCount = env->GetArrayLength(clusterIds);
    jint * clusters = env->GetIntArrayElements(clusterIds, nullptr);
    
    std::vector<chip::ClusterId> requestedClusters;
    for(int i=0; i<clusterCount; i++) {
        requestedClusters.push_back(static_cast<chip::ClusterId>(clusters[i]));
    }
    
    env->ReleaseIntArrayElements(clusterIds, clusters, 0);

    // Parse Device Type IDs
    std::vector<EmberAfDeviceType> requestedDeviceTypes;
    if (deviceTypeIds != nullptr) {
        jsize dtCount = env->GetArrayLength(deviceTypeIds);
        jint * dtIds = env->GetIntArrayElements(deviceTypeIds, nullptr);
        for(int i=0; i<dtCount; i++) {
            requestedDeviceTypes.push_back(EmberAfDeviceType{
                static_cast<uint32_t>(dtIds[i]),
                static_cast<uint8_t>(DEVICE_VERSION_DEFAULT)
            });
        }
        env->ReleaseIntArrayElements(deviceTypeIds, dtIds, 0);
    }
    
    DeviceGeneric * newDevice = new DeviceGeneric(deviceName, "Generic");
    env->ReleaseStringUTFChars(name, deviceName);
    
    // Create Dynamic Endpoint Data
    DynamicEndpointData* epData = new DynamicEndpointData();
    
    // Parse Attributes and group by Cluster
    // Map<ClusterId, vector<EmberAfAttributeMetadata>>
    std::map<chip::ClusterId, std::vector<EmberAfAttributeMetadata>> clusterAttributes;
    
    if (attributes != nullptr) {
        jsize attrCount = env->GetArrayLength(attributes);
        jclass attrClass = env->FindClass("com/matter/bridge/app/ClusterAttribute");
        jfieldID clusterIdField = env->GetFieldID(attrClass, "clusterId", "I");
        jfieldID attributeIdField = env->GetFieldID(attrClass, "attributeId", "I");
        jfieldID typeField = env->GetFieldID(attrClass, "type", "I");
        jfieldID sizeField = env->GetFieldID(attrClass, "size", "I");
        jfieldID maskField = env->GetFieldID(attrClass, "mask", "I");
        
        for(int i=0; i<attrCount; i++) {
            jobject attrObj = env->GetObjectArrayElement(attributes, i);
            
            int cId = env->GetIntField(attrObj, clusterIdField);
            int aId = env->GetIntField(attrObj, attributeIdField);
            int type = env->GetIntField(attrObj, typeField);
            int size = env->GetIntField(attrObj, sizeField);
            int mask = env->GetIntField(attrObj, maskField);
            
            EmberAfAttributeMetadata metadata = {
                .defaultValue = ZAP_EMPTY_DEFAULT(),
                .attributeId = static_cast<chip::AttributeId>(aId),
                .size = static_cast<uint16_t>(size),
                .attributeType = static_cast<EmberAfAttributeType>(type),
                .mask = static_cast<EmberAfAttributeMask>(mask)
            };
            
            clusterAttributes[static_cast<chip::ClusterId>(cId)].push_back(metadata);
            
            env->DeleteLocalRef(attrObj);
        }
    }

    // Automatically add Descriptor cluster if not present
    bool hasDescriptor = false;
    for (auto clusterId : requestedClusters) {
        if (clusterId == Descriptor::Id) {
            hasDescriptor = true;
            break;
        }
    }
    if (!hasDescriptor) {
        requestedClusters.push_back(Descriptor::Id);
        std::vector<EmberAfAttributeMetadata> attrs;
        for (size_t i = 0; i < MATTER_ARRAY_SIZE(descriptorAttrs); i++) {
            attrs.push_back(descriptorAttrs[i]);
        }
        clusterAttributes[Descriptor::Id] = attrs;
    }

    // Automatically add Bridged Device Basic Information cluster if not present and device type is Bridged Node
    bool hasBridgedDeviceBasic = false;
    for (auto clusterId : requestedClusters) {
        if (clusterId == BridgedDeviceBasicInformation::Id) {
            hasBridgedDeviceBasic = true;
            break;
        }
    }
    
    bool isBridgedNode = false;
    for (const auto& dt : requestedDeviceTypes) {
        if (dt.deviceTypeId == DEVICE_TYPE_BRIDGED_NODE) {
            isBridgedNode = true;
            break;
        }
    }

    if (!hasBridgedDeviceBasic && isBridgedNode) {
        requestedClusters.push_back(BridgedDeviceBasicInformation::Id);
        std::vector<EmberAfAttributeMetadata> attrs;
        for (size_t i = 0; i < MATTER_ARRAY_SIZE(bridgedDeviceBasicAttrs); i++) {
            attrs.push_back(bridgedDeviceBasicAttrs[i]);
        }
        clusterAttributes[BridgedDeviceBasicInformation::Id] = attrs;
    }

    // Add requested clusters and their attributes
    for (auto clusterId : requestedClusters) {
        // Add common attributes if not present? 
        // For now, assume Kotlin passes all necessary attributes including revision.
        // Or we can auto-add revision if missing.
        // Let's just use what's passed.
        
        std::vector<EmberAfAttributeMetadata>& attrs = clusterAttributes[clusterId];
        epData->attributes.push_back(attrs);
    }
    
    // Build Cluster structs
    for (size_t i=0; i<epData->attributes.size(); i++) {
        EmberAfCluster cluster;
        cluster.clusterId = requestedClusters[i];
        cluster.attributes = epData->attributes[i].data();
        cluster.attributeCount = static_cast<uint16_t>(epData->attributes[i].size());
        cluster.mask = ZAP_CLUSTER_MASK(SERVER);
        cluster.functions = nullptr;
        
        // Set accepted command list based on cluster ID
        if (cluster.clusterId == OnOff::Id) {
            cluster.acceptedCommandList = onOffIncomingCommands;
        } else {
            cluster.acceptedCommandList = nullptr;
        }
        
        cluster.generatedCommandList = nullptr;
        cluster.eventList = nullptr;
        cluster.eventCount = 0;
        
        epData->clusters.push_back(cluster);
    }
    
    epData->endpointType.cluster = epData->clusters.data();
    epData->endpointType.clusterCount = static_cast<uint8_t>(epData->clusters.size());
    epData->endpointType.endpointSize = 0;
    
    epData->dataVersions = new DataVersion[epData->clusters.size()];
    
    // Store device types in a persistent way
    epData->deviceTypes = requestedDeviceTypes;
    
    gDynamicEndpoints.push_back(epData);
    
    // Schedule work
    struct AddGenericContext {
        DeviceGeneric* device;
        EmberAfEndpointType* epType;
        DataVersion* dataVersions;
        chip::EndpointId endpoint;
        chip::EndpointId parentEndpoint;
        std::vector<EmberAfDeviceType>* deviceTypes;
    };
    
    AddGenericContext* ctx = new AddGenericContext{
        newDevice, 
        &epData->endpointType, 
        epData->dataVersions, 
        static_cast<chip::EndpointId>(endpoint),
        static_cast<chip::EndpointId>(parentEndpointId),
        &epData->deviceTypes
    };
    
    ChipLogProgress(Zcl, "addBridgedDevice: endpoint=%d, parentEndpoint=%d, name=%s, clusterCount=%zu, deviceTypeCount=%zu", 
                    endpoint, parentEndpointId, deviceName, requestedClusters.size(), requestedDeviceTypes.size());
    
    chip::DeviceLayer::PlatformMgr().ScheduleWork(
        [](intptr_t arg) {
            AddGenericContext* ctx = reinterpret_cast<AddGenericContext*>(arg);
            
            ChipLogProgress(Zcl, "addBridgedDevice: ScheduleWork lambda executing for endpoint %d", ctx->endpoint);
            
            ChipLogProgress(Zcl, "addBridgedDevice: Calling AddDeviceEndpoint for endpoint %d", ctx->endpoint);
            
            // Register endpoint with Matter SDK using AddDeviceEndpoint
            int index = AddDeviceEndpoint(
                ctx->device,
                ctx->epType,
                Span<const EmberAfDeviceType>(ctx->deviceTypes->data(), ctx->deviceTypes->size()),
                Span<DataVersion>(ctx->dataVersions, ctx->epType->clusterCount),
                ctx->endpoint,
                #if CHIP_CONFIG_USE_ENDPOINT_UNIQUE_ID
                Span<const char>(), // Empty unique ID for now
                #endif
                ctx->parentEndpoint
            );
            
            if (index >= 0) {
                // gDevices[index] is already set by AddDeviceEndpoint
                gDeviceTypes[index] = DeviceType::Generic;
                gDynamicDevices[index] = true;
                ChipLogProgress(Zcl, "Successfully added generic device '%s' at endpoint %d, index %d", 
                               ctx->device->GetName(), ctx->endpoint, index);
            } else {
                ChipLogError(Zcl, "Failed to add generic device at endpoint %d", ctx->endpoint);
                delete ctx->device;
            }
            
            delete ctx;
        }, reinterpret_cast<intptr_t>(ctx));
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_matter_bridge_app_BridgeApp_updateClusterAttribute__IIIJ(JNIEnv *, jobject, jint endpoint, jint clusterId, jint attributeId, jlong value)
{
    ChipLogProgress(Zcl, "updateClusterAttribute (long): endpoint=%d, cluster=0x%x, attr=0x%x, value=%ld", 
                    endpoint, clusterId, attributeId, (long)value);

    uint16_t endpointIndex = emberAfGetDynamicIndexFromEndpoint(static_cast<chip::EndpointId>(endpoint));
    
    if (endpointIndex >= CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
    {
        ChipLogError(Zcl, "updateClusterAttribute (long): Invalid endpoint %d", endpoint);
        return JNI_FALSE;
    }

    Device * dev = gDevices[endpointIndex];
    if (dev == nullptr)
    {
        ChipLogError(Zcl, "updateClusterAttribute (long): Device not found for endpoint %d", endpoint);
        return JNI_FALSE;
    }

    // Mark attribute as changed - must be done on Matter thread
    chip::DeviceLayer::PlatformMgr().ScheduleWork(
        [](intptr_t context) {
            auto * data = reinterpret_cast<std::tuple<chip::EndpointId, chip::ClusterId, chip::AttributeId>*>(context);
            MatterReportingAttributeChangeCallback(std::get<0>(*data), std::get<1>(*data), std::get<2>(*data));
            delete data;
        },
        reinterpret_cast<intptr_t>(new std::tuple<chip::EndpointId, chip::ClusterId, chip::AttributeId>(
            static_cast<chip::EndpointId>(endpoint),
            static_cast<chip::ClusterId>(clusterId),
            static_cast<chip::AttributeId>(attributeId)
        ))
    );
    
    return JNI_TRUE;
}

// Overload for byte array values (String, complex types)
// Note: JNI requires mangled names for overloaded methods
extern "C" JNIEXPORT jboolean JNICALL Java_com_matter_bridge_app_BridgeApp_updateClusterAttribute__III_3B(JNIEnv * env, jobject, jint endpoint, jint clusterId, jint attributeId, jbyteArray value)
{
    if (value == nullptr)
    {
        ChipLogError(Zcl, "updateClusterAttribute (byte[]): null value array");
        return JNI_FALSE;
    }
    
    jsize valueLen = env->GetArrayLength(value);
    ChipLogProgress(Zcl, "updateClusterAttribute (byte[]): endpoint=%d, cluster=0x%x, attr=0x%x, valueLen=%d", 
                    endpoint, clusterId, attributeId, valueLen);

    uint16_t endpointIndex = emberAfGetDynamicIndexFromEndpoint(static_cast<chip::EndpointId>(endpoint));
    
    if (endpointIndex >= CHIP_DEVICE_CONFIG_DYNAMIC_ENDPOINT_COUNT)
    {
        ChipLogError(Zcl, "updateClusterAttribute (byte[]): Invalid endpoint %d", endpoint);
        return JNI_FALSE;
    }

    Device * dev = gDevices[endpointIndex];
    if (dev == nullptr)
    {
        ChipLogError(Zcl, "updateClusterAttribute (byte[]): Device not found for endpoint %d", endpoint);
        return JNI_FALSE;
    }

    // Get byte array data
    jbyte* bytes = env->GetByteArrayElements(value, nullptr);
    if (bytes == nullptr)
    {
        ChipLogError(Zcl, "updateClusterAttribute (byte[]): Failed to get byte array elements");
        return JNI_FALSE;
    }

    // For now, just trigger the attribute change callback
    // The actual value storage would depend on device implementation
    // Matter stack will read the value via our HandleClusterAttributeRead callback
    
    ChipLogProgress(Zcl, "updateClusterAttribute (byte[]): Triggering attribute change notification");
    
    env->ReleaseByteArrayElements(value, bytes, JNI_ABORT);
    
    // Trigger attribute change callback to report to Matter stack - must be done on Matter thread
    chip::DeviceLayer::PlatformMgr().ScheduleWork(
        [](intptr_t context) {
            auto * data = reinterpret_cast<std::tuple<chip::EndpointId, chip::ClusterId, chip::AttributeId>*>(context);
            MatterReportingAttributeChangeCallback(std::get<0>(*data), std::get<1>(*data), std::get<2>(*data));
            delete data;
        },
        reinterpret_cast<intptr_t>(new std::tuple<chip::EndpointId, chip::ClusterId, chip::AttributeId>(
            static_cast<chip::EndpointId>(endpoint),
            static_cast<chip::ClusterId>(clusterId),
            static_cast<chip::AttributeId>(attributeId)
        ))
    );
    
    return JNI_TRUE;
}



// Stub implementations for methods removed from Java but still potentially called by JNI if not cleaned up
// (Though I cleaned them up in Java, I'll add empty stubs just in case to match the Java file if I missed any)

JNI_METHOD(void, setOnOffManager)(JNIEnv *, jobject, jint endpoint, jobject manager) {}
JNI_METHOD(jboolean, setOnOff)(JNIEnv *, jobject, jint endpoint, jboolean value) { return false; }
JNI_METHOD(void, setDoorLockManager)(JNIEnv *, jobject, jint endpoint, jobject manager) {}
JNI_METHOD(jboolean, setLockType)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
JNI_METHOD(jboolean, setLockState)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
JNI_METHOD(jboolean, setActuatorEnabled)(JNIEnv *, jobject, jint endpoint, jboolean value) { return false; }
JNI_METHOD(jboolean, setAutoRelockTime)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
JNI_METHOD(jboolean, setOperatingMode)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
JNI_METHOD(jboolean, setSupportedOperatingModes)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
JNI_METHOD(jboolean, sendLockAlarmEvent)(JNIEnv *, jobject, jint endpoint) { return false; }
JNI_METHOD(void, setPowerSourceManager)(JNIEnv *, jobject, jint endpoint, jobject manager) {}
JNI_METHOD(jboolean, setBatPercentRemaining)(JNIEnv *, jobject, jint endpoint, jint value) { return false; }
