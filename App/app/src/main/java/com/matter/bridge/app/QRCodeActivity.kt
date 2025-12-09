package com.matter.bridge.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import timber.log.Timber

class QRCodeActivity : AppCompatActivity() {

  private lateinit var qrCodeImageView: ImageView
  private lateinit var qrCodeTextView: TextView
  private lateinit var bridgeApp: BridgeApp

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_qrcode)

    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = "Commissioning QR Code"

    qrCodeImageView = findViewById(R.id.qrCodeImageView)
    qrCodeTextView = findViewById(R.id.qrCodeTextView)

    // Get bridgeApp instance from MainActivity (passed via companion object)
    bridgeApp = MainActivity.bridgeAppInstance ?: run {
      Timber.e("BridgeApp not initialized")
      qrCodeTextView.text = "Error: Bridge App not initialized.\nPlease return to main screen."
      return
    }
    
    displayQRCode()
  }

  private fun displayQRCode() {
    try {
      val qrCodeString = bridgeApp.getCommissioningQRCode()
      Timber.d("QR Code: $qrCodeString")

      if (qrCodeString.isEmpty()) {
        qrCodeTextView.text = "Error: QR code generation failed"
        return
      }

      // Generate QR code bitmap
      val qrCodeBitmap = generateQRCode(qrCodeString, 512, 512)
      qrCodeImageView.setImageBitmap(qrCodeBitmap)
      
      // Display the text version
      qrCodeTextView.text = "Manual Pairing Code:\n$qrCodeString"
    } catch (e: Exception) {
      Timber.e(e, "Error generating QR code")
      qrCodeTextView.text = "Error generating QR code: ${e.message}"
    }
  }

  private fun generateQRCode(content: String, width: Int, height: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    
    for (x in 0 until width) {
      for (y in 0 until height) {
        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
      }
    }
    
    return bitmap
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }
}
