package com.matter.bridge.app

import android.app.Dialog
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText

class AddDeviceDialog(private val onAddDevice: (String, Int, DeviceType) -> Unit) : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = AlertDialog.Builder(requireContext())
    val inflater = requireActivity().layoutInflater
    val view = inflater.inflate(R.layout.dialog_add_device, null)

    val nameInput = view.findViewById<TextInputEditText>(R.id.deviceNameInput)
    val endpointInput = view.findViewById<TextInputEditText>(R.id.endpointInput)
    val typeGroup = view.findViewById<RadioGroup>(R.id.deviceTypeGroup)

    builder.setView(view)
      .setTitle("Add New Device")
      .setPositiveButton("Add") { _, _ ->
        val name = nameInput.text.toString()
        val endpointStr = endpointInput.text.toString()
        val type = if (view.findViewById<RadioButton>(R.id.typeLight).isChecked) {
          DeviceType.LIGHT
        } else {
          DeviceType.TEMP_SENSOR
        }

        if (name.isNotEmpty() && endpointStr.isNotEmpty()) {
          val endpoint = endpointStr.toIntOrNull()
          if (endpoint != null) {
            onAddDevice(name, endpoint, type)
          }
        }
      }
      .setNegativeButton("Cancel") { dialog, _ ->
        dialog.cancel()
      }

    return builder.create()
  }
}
