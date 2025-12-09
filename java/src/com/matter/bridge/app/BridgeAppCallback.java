/*
 *
 *    Copyright (c) 2023 Project CHIP Authors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.matter.bridge.app;

public interface BridgeAppCallback {
  void onClusterInit(BridgeApp app, long clusterId, int endpoint);

  void onEvent(long event);

  /**
   * Called when a bridged device's state is changed by Matter Controller.
   * @param endpoint The endpoint ID
   * @param clusterId The cluster ID
   * @param attributeId The attribute ID  
   * @param value The raw attribute value bytes (can be decoded based on cluster/attribute type)
   */
  void onDeviceStateChanged(int endpoint, int clusterId, int attributeId, byte[] value);

  /**
   * Called when Matter stack needs to read an attribute value.
   * @param endpoint The endpoint ID
   * @param clusterId The cluster ID
   * @param attributeId The attribute ID
   * @param maxReadLength Maximum bytes that can be returned
   * @return Attribute value encoded as byte array, or null if not handled
   */
  byte[] onClusterAttributeRead(int endpoint, int clusterId, int attributeId, int maxReadLength);

  /**
   * Called when Matter stack receives an attribute write request.
   * @param endpoint The endpoint ID
   * @param clusterId The cluster ID
   * @param attributeId The attribute ID
   * @param value The raw attribute value bytes
   * @return true if write was handled successfully, false otherwise
   */
  boolean onClusterAttributeWrite(int endpoint, int clusterId, int attributeId, byte[] value);

  /**
   * Called when Matter stack receives a cluster command.
   * @param endpoint The endpoint ID
   * @param clusterId The cluster ID
   * @param commandId The command ID
   * @return true if command was handled successfully, false otherwise
   */
  boolean onClusterCommand(int endpoint, int clusterId, int commandId);
}
