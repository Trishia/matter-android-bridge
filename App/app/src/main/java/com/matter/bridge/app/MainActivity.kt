package com.matter.bridge.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class MainActivity : AppCompatActivity() {

  companion object {
    var bridgeAppInstance: BridgeApp? = null
  }

  private lateinit var recyclerView: RecyclerView
  private lateinit var deviceAdapter: DeviceAdapter
  private val devices = mutableListOf<BridgedDevice>()
  private var bridgeApp: BridgeApp? = null
  private var chipAppServer: chip.appserver.ChipAppServer? = null

  private val permissions = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION
  )

  private val requestMultiplePermissions =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
      permissions.entries.forEach { Timber.d("${it.key}:${it.value}") }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      Timber.d("onCreate()")

      setContentView(R.layout.activity_main)

      // Setup RecyclerView first (will populate later)
      recyclerView = findViewById(R.id.devicesRecyclerView)
      recyclerView.layoutManager = LinearLayoutManager(this)

      // Setup FAB (disabled until BridgeApp is ready)
      findViewById<FloatingActionButton>(R.id.addDeviceFab).apply {
          isEnabled = false
          setOnClickListener {
              showAddDeviceDialog()
          }
      }

      Snackbar.make(
          recyclerView,
          "Initializing Matter SDK...",
          Snackbar.LENGTH_SHORT
      ).show()

      Timber.d("Starting Matter SDK initialization in background")

      // Initialize BridgeApp with full Matter SDK
      Thread {
          try {
              Timber.d("Step 1: Creating BridgeApp instance...")
              val app = BridgeApp()
              app.setDACProvider(DACProviderStub())
              DeviceFactory.initialize(app)
              
              // Set up callback to handle device state changes from Matter Controller
              app.setCallback(object : BridgeAppCallback {
                  override fun onClusterInit(app: BridgeApp, clusterId: Long, endpoint: Int) {
                      Timber.d("Cluster initialized: cluster=0x${clusterId.toString(16)}, endpoint=$endpoint")
                  }
                  
                  override fun onEvent(event: Long) {
                      Timber.d("Event received: $event")
                  }
                  
                  override fun onDeviceStateChanged(
                      endpoint: Int,
                      clusterId: Int,
                      attributeId: Int,
                      value: ByteArray?
                  ) {
                      if (value == null || value.isEmpty()) {
                          Timber.w("Device state changed with null/empty value")
                          return
                      }
                      
                      Timber.i("Device state changed - Endpoint: $endpoint, Cluster: 0x${clusterId.toString(16)}, Attribute: 0x${attributeId.toString(16)}, ValueLength: ${value.size}")
                      runOnUiThread {
                          handleDeviceStateChange(endpoint, clusterId, attributeId, value)
                      }
                  }
                  
                  override fun onClusterAttributeRead(
                      endpoint: Int,
                      clusterId: Int,
                      attributeId: Int,
                      maxReadLength: Int
                  ): ByteArray? {
                      return handleClusterAttributeRead(endpoint, clusterId, attributeId, maxReadLength)
                  }
                  
                  override fun onClusterAttributeWrite(
                      endpoint: Int,
                      clusterId: Int,
                      attributeId: Int,
                      value: ByteArray
                  ): Boolean {
                      return handleClusterAttributeWrite(endpoint, clusterId, attributeId, value)
                  }

                  override fun onClusterCommand(
                      endpoint: Int,
                      clusterId: Int,
                      commandId: Int
                  ): Boolean {
                      return handleClusterCommand(endpoint, clusterId, commandId)
                  }
              })
              
              Timber.d("Step 2: Calling preServerInit...")
              app.preServerInit()
              
              // Initialize devices BEFORE starting the server to avoid race conditions
              // where read requests come in before devices are populated
              bridgeApp = app
              bridgeAppInstance = app
              initializeDevices()
              
              // Update UI on main thread
              runOnUiThread {
                  deviceAdapter = DeviceAdapter(devices, bridgeApp, { device ->
                      onDeleteDevice(device)
                  }, this@MainActivity)
                  recyclerView.adapter = deviceAdapter

                  // Enable FAB
                  findViewById<FloatingActionButton>(R.id.addDeviceFab).isEnabled = true
              }

              Timber.d("Step 3: Starting ChipAppServer...")
              chipAppServer = chip.appserver.ChipAppServer()
              chipAppServer?.startAppWithDelegate(
                  object : chip.appserver.ChipAppServerDelegate {
                      override fun onCommissioningSessionEstablishmentStarted() {
                          Timber.d("Commissioning session establishment started")
                      }
                      
                      override fun onCommissioningSessionStarted() {
                          Timber.d("Commissioning session started")
                      }
                      
                      override fun onCommissioningSessionEstablishmentError(errorCode: Int) {
                          Timber.e("Commissioning error: $errorCode")
                      }
                      
                      override fun onCommissioningSessionStopped() {
                          Timber.d("Commissioning session stopped")
                      }
                      
                      override fun onCommissioningWindowOpened() {
                          Timber.d("Commissioning window opened")
                      }
                      
                      override fun onCommissioningWindowClosed() {
                          Timber.d("Commissioning window closed")
                      }
                  }
              )
              
              Timber.d("Step 4: Calling postServerInit (Matter server is running)...")
              app.postServerInit(22) // 22 = Aggregator device type for bridge
              Timber.d("Matter SDK initialized successfully with full functionality")

              // Update UI on main thread
              runOnUiThread {
                  Snackbar.make(
                      recyclerView,
                      "Matter SDK initialized - ${devices.size} devices ready",
                      Snackbar.LENGTH_LONG
                  ).show()

                  Timber.d("UI setup completed successfully")
              }
          } catch (e: Exception) {
              Timber.e(e, "Failed to initialize Matter SDK")
              runOnUiThread {
                  Toast.makeText(
                      this,
                      "Failed to initialize Matter SDK: ${e.message}",
                      Toast.LENGTH_LONG
                  ).show()
                  Snackbar.make(
                      recyclerView,
                      "Error: ${e.message}",
                      Snackbar.LENGTH_LONG
                  ).show()
              }
          }
      }.start()

      Timber.d("Matter SDK initialization started in background")
  }

  private fun showAddDeviceDialog() {
    val dialog = AddDeviceDialog { name, endpoint, type ->
      onAddDevice(name, endpoint, type)
    }
    dialog.show(supportFragmentManager, "AddDeviceDialog")
  }

  private fun onAddDevice(name: String, endpoint: Int, type: DeviceType) {
    // Check if endpoint already exists
    if (devices.any { it.endpoint == endpoint }) {
      Toast.makeText(this, "Endpoint $endpoint already exists", Toast.LENGTH_SHORT).show()
      return
    }

    // Use DeviceFactory which uses Generic API internally
    val newDevice = when (type) {
      DeviceType.LIGHT -> DeviceFactory.createLight(name, endpoint)
      DeviceType.TEMP_SENSOR -> DeviceFactory.createTempSensor(name, endpoint)
      else -> {
        Toast.makeText(this, "Unsupported device type", Toast.LENGTH_SHORT).show()
        return
      }
    }
    
    devices.add(newDevice)
    devices.sortBy { it.endpoint }
    deviceAdapter.notifyDataSetChanged()
    Toast.makeText(this, "Device added successfully", Toast.LENGTH_SHORT).show()
  }

  private fun onDeleteDevice(device: BridgedDevice) {
    val success = bridgeApp?.removeBridgedDevice(device.endpoint) ?: false
    
    if (success) {
      devices.remove(device)
      deviceAdapter.notifyDataSetChanged()
      Toast.makeText(this, "Device removed successfully", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(this, "Failed to remove device", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_qr_code -> {
        showQRCode()
        true
      }
      R.id.action_factory_reset -> {
        showFactoryResetConfirmation()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }
  
  private fun showFactoryResetConfirmation() {
    androidx.appcompat.app.AlertDialog.Builder(this)
      .setTitle("Factory Reset")
      .setMessage("This will erase all Matter commissioning data and restart the app. Are you sure?")
      .setPositiveButton("Reset") { _, _ ->
        performFactoryReset()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }
  
  private fun performFactoryReset() {
    Timber.w("Performing factory reset...")
    
    try {
      // Stop the server first
      chipAppServer?.stopApp()
      
      // Clear all devices
      devices.clear()
      deviceAdapter?.notifyDataSetChanged()
      
      // On Android, factory reset requires clearing persistent storage and restarting
      // This typically involves:
      // 1. Clearing SharedPreferences/DataStore
      // 2. Clearing any KVS (Key-Value Store) data
      // 3. Restarting the application
      
      Toast.makeText(this, "Factory reset complete. Please restart the app manually.", Toast.LENGTH_LONG).show()
      
      // Option: Force app restart
      // val intent = packageManager.getLaunchIntentForPackage(packageName)
      // intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      // startActivity(intent)
      // finish()
      // exitProcess(0)
      
    } catch (e: Exception) {
      Timber.e(e, "Factory reset failed")
      Toast.makeText(this, "Factory reset failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun showQRCode() {
    val intent = Intent(this, QRCodeActivity::class.java)
    startActivity(intent)
  }

  private fun initializeDevices() {
    // Devices are now initialized from Kotlin using ServerInitializer
    // This replaces the hardcoded device initialization that was in C++ BridgeApp-JNI.cpp
    ServerInitializer.initializeDefaultDevices(devices)
  }

  /**
   * Handle device state changes from Matter Controller
   */
  private fun handleDeviceStateChange(endpoint: Int, clusterId: Int, attributeId: Int, value: ByteArray) {
      val device = devices.find { it.endpoint == endpoint }
      if (device == null) {
          Timber.w("Received state change for unknown endpoint: $endpoint")
          return
      }
      
      Timber.d("Handling state change for device: ${device.name} (${device.type})")
      /*
        // Boolean (1 byte)
        val isOn = value[0] != 0.toByte()

        // Int16 (2 bytes, little-endian)
        val temp = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)

        // String (UTF-8)
        val name = String(value, Charsets.UTF_8)
      */

      when (clusterId) {
          MatterConstants.OnOff.CLUSTER_ID -> {
              if (device is BridgedDevice.Light && attributeId == MatterConstants.OnOff.Attributes.ON_OFF && value.isNotEmpty()) {
                  val newState = value[0] != 0.toByte()
                  Timber.i("💡 Light '${device.name}' turned ${if (newState) "ON" else "OFF"} by Matter Controller")
                  device.isOn = newState
                  deviceAdapter.notifyDataSetChanged()
                  
                  Toast.makeText(
                      this,
                      "${device.name}: ${if (newState) "ON" else "OFF"}",
                      Toast.LENGTH_SHORT
                  ).show()
              }
          }
          
          MatterConstants.TemperatureMeasurement.CLUSTER_ID -> {
              if (device is BridgedDevice.TemperatureSensor &&
                  attributeId == MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE &&
                  value.size >= 2) {
                  // Decode int16 (little-endian)
                  val temp = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
                  val signedTemp = temp.toShort().toInt()
                  Timber.i("🌡️ Temperature sensor '${device.name}' updated to ${signedTemp / 100.0}°C by Matter Controller")
                  device.temperature = signedTemp
                  deviceAdapter.notifyDataSetChanged()
                  
                  Toast.makeText(
                      this,
                      "${device.name}: ${signedTemp / 100.0}°C",
                      Toast.LENGTH_SHORT
                  ).show()
              }
          }
          
          MatterConstants.BridgedDeviceBasicInformation.CLUSTER_ID -> {
              Timber.i("📝 Device '${device.name}' basic info updated (attr: 0x${attributeId.toString(16)})")
              // Handle name changes or other basic information updates
              // For String attributes like NodeLabel, value contains the UTF-8 encoded string
              if(attributeId == MatterConstants.BridgedDeviceBasicInformation.Attributes.NODE_LABEL) {
                  device.name = String(value, Charsets.UTF_8)
                  deviceAdapter.notifyDataSetChanged()
                  Toast.makeText(
                      this,
                      "${device.name}: ${String(value, Charsets.UTF_8)}",
                      Toast.LENGTH_SHORT
                  ).show()
              }
              deviceAdapter.notifyDataSetChanged()
          }
          
          else -> {
              Timber.d("Unhandled cluster state change: cluster=0x${clusterId.toString(16)}, device=${device.name}")
              // For generic devices, just refresh the UI
              deviceAdapter.notifyDataSetChanged()
          }
      }
  }

  /**
   * Handle cluster attribute read requests from Matter stack.
   * Returns encoded attribute value or null if not handled.
   */
  private fun handleClusterAttributeRead(
      endpoint: Int,
      clusterId: Int,
      attributeId: Int,
      maxReadLength: Int
  ): ByteArray? {
      val device = devices.find { it.endpoint == endpoint } ?: return null
      
      return when (clusterId) {
          MatterConstants.OnOff.CLUSTER_ID -> {
              if (device is BridgedDevice.Light) {
                  if (attributeId == MatterConstants.OnOff.Attributes.ON_OFF) {
                      val value: Byte = if (device.isOn) 1 else 0
                      byteArrayOf(value)
                  } else if (attributeId == MatterConstants.OnOff.Attributes.CLUSTER_REVISION) {
                      byteArrayOf(0x04, 0x00) // Revision 4
                  } else {
                      null
                  }
              } else {
                  Timber.w("Device ${device.name} is not a Light (is ${device.javaClass.simpleName})")
                  null
              }
          }
          
          MatterConstants.TemperatureMeasurement.CLUSTER_ID -> {
              when (attributeId) {
                  MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE -> {
                      if (device is BridgedDevice.TemperatureSensor) {
                          Timber.d("Read Temperature: ${device.temperature / 100.0}°C")
                          // Encode as int16 (2 bytes, little-endian)
                          val temp = device.temperature.toShort()
                          byteArrayOf(
                              (temp.toInt() and 0xFF).toByte(),
                              ((temp.toInt() shr 8) and 0xFF).toByte()
                          )
                      } else null
                  }
                  MatterConstants.TemperatureMeasurement.Attributes.MIN_MEASURED_VALUE -> {
                      // Min: -10°C = -1000 (centidegrees)
                      val min: Short = -1000
                      byteArrayOf(
                          (min.toInt() and 0xFF).toByte(),
                          ((min.toInt() shr 8) and 0xFF).toByte()
                      )
                  }
                  MatterConstants.TemperatureMeasurement.Attributes.MAX_MEASURED_VALUE -> {
                      // Max: 50°C = 5000 (centidegrees)
                      val max: Short = 5000
                      byteArrayOf(
                          (max.toInt() and 0xFF).toByte(),
                          ((max.toInt() shr 8) and 0xFF).toByte()
                      )
                  }
                  MatterConstants.TemperatureMeasurement.Attributes.CLUSTER_REVISION -> {
                      byteArrayOf(0x04, 0x00) // Revision 4
                  }
                  else -> null
              }
          }
          MatterConstants.RelativeHumidityMeasurement.CLUSTER_ID -> {
              when (attributeId) {
                  MatterConstants.RelativeHumidityMeasurement.Attributes.MEASURED_VALUE -> {
                      if (device is BridgedDevice.HumiditySensor) {
                          Timber.d("Read RelativeHumidity: ${device.humidity / 100.0}°C")
                          // Encode as int16 (2 bytes, little-endian)
                          val temp = device.humidity.toShort()
                          byteArrayOf(
                              (temp.toInt() and 0xFF).toByte(),
                              ((temp.toInt() shr 8) and 0xFF).toByte()
                          )
                      } else null
                  }
                  MatterConstants.RelativeHumidityMeasurement.Attributes.MIN_MEASURED_VALUE -> {
                      // Min: 0% = 0 (Percent)
                      val min: Short = 0
                      byteArrayOf(
                          (min.toInt() and 0xFF).toByte(),
                          ((min.toInt() shr 8) and 0xFF).toByte()
                      )
                  }
                  MatterConstants.RelativeHumidityMeasurement.Attributes.MAX_MEASURED_VALUE -> {
                      // Max: 100% = 10000 (Percent)
                      val max: Short = 10000
                      byteArrayOf(
                          (max.toInt() and 0xFF).toByte(),
                          ((max.toInt() shr 8) and 0xFF).toByte()
                      )
                  }
                  MatterConstants.TemperatureMeasurement.Attributes.CLUSTER_REVISION -> {
                      byteArrayOf(0x04, 0x00) // Revision 4
                  }
                  else -> null
              }
          }
          else -> {
              Timber.w("Unhandled cluster read: 0x${clusterId.toString(16)}")
              null
          }
      }
  }

  /**
   * Handle cluster attribute write requests from Matter stack.
   * Returns true if write was handled, false otherwise.
   */
  private fun handleClusterAttributeWrite(
      endpoint: Int,
      clusterId: Int,
      attributeId: Int,
      value: ByteArray
  ): Boolean {
      val device = devices.find { it.endpoint == endpoint }
      if (device == null) {
          Timber.w("Write request for unknown endpoint: $endpoint")
          return false
      }
      
      Timber.d("Handling attribute write: device=${device.name}, cluster=0x${clusterId.toString(16)}, attr=0x${attributeId.toString(16)}, valueLen=${value.size}")
      
      return when (clusterId) {
          MatterConstants.OnOff.CLUSTER_ID -> {
              if (device is BridgedDevice.Light && attributeId == MatterConstants.OnOff.Attributes.ON_OFF && value.isNotEmpty()) {
                  val newState = value[0] != 0.toByte()
                  Timber.i("💡 Write OnOff: ${device.name} -> ${if (newState) "ON" else "OFF"}")
                  device.isOn = newState
                  runOnUiThread {
                      deviceAdapter.notifyDataSetChanged()
                  }
                  true
              } else false
          }
          
          MatterConstants.TemperatureMeasurement.CLUSTER_ID -> {
              if (device is BridgedDevice.TemperatureSensor &&
                  attributeId == MatterConstants.TemperatureMeasurement.Attributes.MEASURED_VALUE && 
                  value.size >= 2) {
                  // Decode int16 (little-endian)
                  val temp = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
                  val signedTemp = temp.toShort().toInt()
                  Timber.i("🌡️ Write Temperature: ${device.name} -> ${signedTemp / 100.0}°C")
                  device.temperature = signedTemp
                  runOnUiThread {
                      deviceAdapter.notifyDataSetChanged()
                  }
                  true
              } else false
          }
          
          else -> {
              Timber.w("Unhandled cluster write: 0x${clusterId.toString(16)}")
              false
          }
      }
  }

  /**
   * Handle cluster command requests from Matter stack.
   * Returns true if handled, false otherwise.
   */
  private fun handleClusterCommand(
      endpoint: Int,
      clusterId: Int,
      commandId: Int
  ): Boolean {
      val device = devices.find { it.endpoint == endpoint } ?: return false
      
      if (clusterId == MatterConstants.OnOff.CLUSTER_ID && device is BridgedDevice.Light) {
          when (commandId) {
              MatterConstants.OnOff.Commands.OFF -> {
                  device.isOn = false
                  runOnUiThread { deviceAdapter.notifyDataSetChanged() }
                  return true
              }
              MatterConstants.OnOff.Commands.ON -> {
                  device.isOn = true
                  runOnUiThread { deviceAdapter.notifyDataSetChanged() }
                  return true
              }
              MatterConstants.OnOff.Commands.TOGGLE -> {
                  device.isOn = !device.isOn
                  runOnUiThread { deviceAdapter.notifyDataSetChanged() }
                  return true
              }
          }
      }
       return false
  }
  
  /**
   * Update the reachability status of a bridged device.
   * This allows reporting when a bridged device goes offline or comes back online.
   */
  fun updateDeviceReachability(endpoint: Int, reachable: Boolean) {
      val device = devices.find { it.endpoint == endpoint }
      if (device == null) {
          Timber.w("updateDeviceReachability: Device not found for endpoint $endpoint")
          return
      }
      
      if (device.isReachable != reachable) {
          device.isReachable = reachable
          
          // No need to call notifyDataSetChanged() here - the adapter will reflect
          // the change when needed, and calling it during onBind causes crashes
          
          // Report change to Matter stack
          bridgeApp?.reportAttributeChange(
              endpoint,
              MatterConstants.BridgedDeviceBasicInformation.CLUSTER_ID,
              MatterConstants.BridgedDeviceBasicInformation.Attributes.REACHABLE
          )
          
          Timber.i("Device ${device.name} reachability: $reachable")
      }
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.d("onDestroy()")
  }
}
