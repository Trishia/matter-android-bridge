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

#pragma once

#include <jni.h>
#include <lib/support/JniReferences.h>
#include <lib/support/JniTypeWrappers.h>

class BridgeAppJNI
{
public:
    void InitializeWithObjects(jobject app);
    void PostClusterInit(int clusterId, int endpoint);
    void PostEvent(int event);
    void PostDeviceStateChanged(int endpoint, int clusterId, int attributeId, uint8_t* value, size_t valueSize);
    
    // Generic cluster attribute handlers (returns nullptr/false if not handled by Java)
    chip::JniByteArray HandleClusterAttributeRead(int endpoint, int clusterId, int attributeId, int maxReadLength);
    bool HandleClusterAttributeWrite(int endpoint, int clusterId, int attributeId, uint8_t* buffer, size_t bufferSize);
    bool HandleCommand(int endpoint, int clusterId, int commandId);
    void ReportAttributeChange(int endpoint, int clusterId, int attributeId);

    static BridgeAppJNI & GetInstance() { return sInstance; }

private:
    static BridgeAppJNI sInstance;
    chip::JniGlobalReference mDeviceAppObject;
    jmethodID mPostClusterInitMethod = nullptr;
    jmethodID mPostEventMethod       = nullptr;
    jmethodID mPostDeviceStateChangedMethod = nullptr;
    jmethodID mOnAttributeReadMethod = nullptr;
    jmethodID mOnAttributeWriteMethod = nullptr;
    jmethodID mOnCommandMethod = nullptr;
};

inline class BridgeAppJNI & BridgeAppJNIMgr()
{
    return BridgeAppJNI::GetInstance();
}
