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
package com.matter.bridge.app;

import android.util.Log;
import com.matter.bridge.app.ClusterAttribute;
import com.matter.bridge.app.DACProvider;

public class BridgeApp {
  private BridgeAppCallback mCallback = null;
  private static final String TAG = "BridgeApp";

  public BridgeApp() {
    nativeInit();
  }

  public void setCallback(BridgeAppCallback callback) {
    mCallback = callback;
  }

  private void postClusterInit(long clusterId, int endpoint) {
    Log.d(TAG, "postClusterInit for " + clusterId + " at " + endpoint);
    if (mCallback != null) {
      mCallback.onClusterInit(this, clusterId, endpoint);
    }
  }

  private void postEvent(long event) {
    Log.d(TAG, "postEvent : " + event);
    if (mCallback != null) {
      mCallback.onEvent(event);
    }
  }

  private void postDeviceStateChanged(int endpoint, int clusterId, int attributeId, byte[] value) {
    Log.d(TAG, "postDeviceStateChanged: endpoint=" + endpoint + ", cluster=0x" + 
          Integer.toHexString(clusterId) + ", attr=0x" + Integer.toHexString(attributeId) + 
          ", valueLength=" + (value != null ? value.length : 0));
    if (mCallback != null) {
      mCallback.onDeviceStateChanged(endpoint, clusterId, attributeId, value);
    }
  }

  private byte[] onClusterAttributeReadRequest(int endpoint, int clusterId, int attributeId, int maxReadLength) {
    Log.d(TAG, "onClusterAttributeReadRequest: endpoint=" + endpoint + ", cluster=0x" + 
          Integer.toHexString(clusterId) + ", attr=0x" + Integer.toHexString(attributeId));
    if (mCallback != null) {
      return mCallback.onClusterAttributeRead(endpoint, clusterId, attributeId, maxReadLength);
    }
    return null;
  }

  private boolean onClusterAttributeWriteRequest(int endpoint, int clusterId, int attributeId, byte[] value) {
    Log.d(TAG, "onClusterAttributeWriteRequest: endpoint=" + endpoint + ", cluster=0x" + 
          Integer.toHexString(clusterId) + ", attr=0x" + Integer.toHexString(attributeId) + 
          ", valueLength=" + (value != null ? value.length : 0));
    if (mCallback != null) {
      return mCallback.onClusterAttributeWrite(endpoint, clusterId, attributeId, value);
    }
    return false;
  }

  public boolean onClusterCommandRequest(int endpoint, int clusterId, int commandId) {
    Log.d(TAG, "onClusterCommandRequest: ep=" + endpoint + ", cluster=0x" + 
          Integer.toHexString(clusterId) + ", cmd=0x" + Integer.toHexString(commandId));
    if (mCallback != null) {
      return mCallback.onClusterCommand(endpoint, clusterId, commandId);
    }
    return false;
  }

  public native void reportAttributeChange(int endpoint, int clusterId, int attributeId);

  public native void nativeInit();

  // called before Matter server is initiated
  public native void preServerInit();

  // called after Matter server is initiated
  public native void postServerInit(int deviceTypeId);

  public native void setDACProvider(DACProvider provider);

  public native boolean removeBridgedDevice(int endpoint);
  
  public native String getCommissioningQRCode();

  // Device Management API
  public native boolean addBridgedDevice(int endpoint, int parentEndpointId, String name, int[] clusterIds, ClusterAttribute[] attributes, int[] deviceTypeIds);
  
  // Update attribute with Long value (for numeric types)
  public native boolean updateClusterAttribute(int endpoint, int clusterId, int attributeId, long value);
  
  // TODO: Update attribute with byte array value (for String and complex types)
  // Currently commented out due to JNI method signature conflict
  public native boolean updateClusterAttribute(int endpoint, int clusterId, int attributeId, byte[] value);

  static {
    System.loadLibrary("BridgeApp");
  }
}
