package com.matter.bridge.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DeviceAdapter(
  private val devices: List<BridgedDevice>,
  private val bridgeApp: BridgeApp?,
  private val onDeleteDevice: (BridgedDevice) -> Unit,
  private val mainActivity: MainActivity
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

  class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val deviceName: TextView = view.findViewById(R.id.deviceName)
    val deviceType: TextView = view.findViewById(R.id.deviceType)
    val deviceStatus: TextView = view.findViewById(R.id.deviceStatus)
    val deviceEndpoint: TextView = view.findViewById(R.id.deviceEndpoint)
    val deviceReachability: TextView = view.findViewById(R.id.deviceReachability)
    val deviceSwitch: SwitchCompat = view.findViewById(R.id.deviceSwitch)
    val reachabilitySwitch: SwitchCompat = view.findViewById(R.id.reachabilitySwitch)
    val temperatureControls: LinearLayout = view.findViewById(R.id.temperatureControls)
    val temperatureValue: TextView = view.findViewById(R.id.temperatureValue)
    val tempIncreaseButton: MaterialButton = view.findViewById(R.id.tempIncreaseButton)
    val tempDecreaseButton: MaterialButton = view.findViewById(R.id.tempDecreaseButton)
    val deleteButton: android.widget.ImageButton = view.findViewById(R.id.deleteButton)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_device, parent, false)
    return DeviceViewHolder(view)
  }

  override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
    val device = devices[position]
    holder.deviceName.text = device.name
    holder.deviceType.text = device.type.name
    holder.deviceEndpoint.text = "Endpoint: ${device.endpoint}"
    
    // Show reachability status and toggle ONLY for devices with BRIDGED_NODE device type
    if (device.isBridgedNode) {
        holder.deviceReachability.visibility = View.VISIBLE
        holder.reachabilitySwitch.visibility = View.VISIBLE
        
        // Update reachability status text
        val reachabilityText = if (device.isReachable) "Status: ● Online" else "Status: ○ Offline"
        val reachabilityColor = if (device.isReachable) 
            android.graphics.Color.parseColor("#4CAF50")  // Green
        else 
            android.graphics.Color.parseColor("#F44336")  // Red
        holder.deviceReachability.text = reachabilityText
        holder.deviceReachability.setTextColor(reachabilityColor)
        
        // Set reachability switch state WITHOUT triggering listener during bind
        holder.reachabilitySwitch.setOnCheckedChangeListener(null)  // Remove listener temporarily
        holder.reachabilitySwitch.isChecked = device.isReachable    // Set state
        holder.reachabilitySwitch.setOnCheckedChangeListener { _, isChecked ->  // Re-attach listener
            // Only update if state actually changed
            if (device.isReachable != isChecked) {
                mainActivity.updateDeviceReachability(device.endpoint, isChecked)
                
                // Update UI immediately
                val newText = if (isChecked) "Status: ● Online" else "Status: ○ Offline"
                val newColor = if (isChecked)
                    android.graphics.Color.parseColor("#4CAF50")
                else
                    android.graphics.Color.parseColor("#F44336")
                holder.deviceReachability.text = newText
                holder.deviceReachability.setTextColor(newColor)
            }
        }
    } else {
        // Hide reachability UI for devices without BRIDGED_NODE device type
        holder.deviceReachability.visibility = View.GONE
        holder.reachabilitySwitch.visibility = View.GONE
    }
    
    holder.deleteButton.setOnClickListener {
      onDeleteDevice(device)
    }
    
    // Use sealed class type-safe access
    when (device) {
      is BridgedDevice.Light -> {
        // Show switch for lights
        holder.deviceSwitch.visibility = View.VISIBLE
        holder.deviceStatus.visibility = View.GONE
        holder.temperatureControls.visibility = View.GONE
        
        holder.deviceSwitch.isChecked = device.isOn
        holder.deviceSwitch.setOnCheckedChangeListener { _, isChecked ->
          device.setOnOff(bridgeApp!!, isChecked)
        }
      }
      
      is BridgedDevice.TemperatureSensor -> {
        // Show temperature controls for sensors
        holder.deviceSwitch.visibility = View.GONE
        holder.deviceStatus.visibility = View.GONE
        holder.temperatureControls.visibility = View.VISIBLE
        
        val tempCelsius = device.temperature / 100.0
        holder.temperatureValue.text = String.format("%.1f°C", tempCelsius)
        
        holder.tempIncreaseButton.setOnClickListener {
          val newTemp = device.temperature + 100
          device.setTemperature(bridgeApp!!, newTemp)
          holder.temperatureValue.text = String.format("%.1f°C", newTemp / 100.0)
        }
        
        holder.tempDecreaseButton.setOnClickListener {
          val newTemp = device.temperature - 100
          device.setTemperature(bridgeApp!!, newTemp)
          holder.temperatureValue.text = String.format("%.1f°C", newTemp / 100.0)
        }
      }
      
      is BridgedDevice.HumiditySensor -> {
        // Show humidity controls for humidity sensors
        holder.deviceSwitch.visibility = View.GONE
        holder.deviceStatus.visibility = View.GONE
        holder.temperatureControls.visibility = View.VISIBLE
        
        val humidityPercent = device.humidity / 100.0
        holder.temperatureValue.text = String.format("%.1f%%", humidityPercent)
        
        holder.tempIncreaseButton.setOnClickListener {
          val newHumidity = device.humidity + 100
          device.setHumidity(bridgeApp!!, newHumidity)
          holder.temperatureValue.text = String.format("%.1f%%", newHumidity / 100.0)
        }
        
        holder.tempDecreaseButton.setOnClickListener {
          val newHumidity = device.humidity - 100
          device.setHumidity(bridgeApp!!, newHumidity)
          holder.temperatureValue.text = String.format("%.1f%%", newHumidity / 100.0)
        }
      }
      
      is BridgedDevice.Composed -> {
        // For composed devices, show status
        holder.deviceSwitch.visibility = View.GONE
        holder.temperatureControls.visibility = View.GONE
        holder.deviceStatus.visibility = View.VISIBLE
        holder.deviceStatus.text = "Composed Device (Parent)"
      }
      
      is BridgedDevice.Generic -> {
        // For generic devices, show status
        holder.deviceSwitch.visibility = View.GONE
        holder.temperatureControls.visibility = View.GONE
        holder.deviceStatus.visibility = View.VISIBLE
        holder.deviceStatus.text = "Generic (${device.clusterIds.size} clusters)"
      }
    }
  }

  override fun getItemCount() = devices.size
}
