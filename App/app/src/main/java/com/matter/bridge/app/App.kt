package com.matter.bridge.app

import android.app.Application
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.AndroidNfcCommissioningManager
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.ConfigurationManager
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import timber.log.Timber

class App : Application() {
  
  private var androidChipPlatform: AndroidChipPlatform? = null
  
  override fun onCreate() {
    super.onCreate()
    Timber.plant(Timber.DebugTree())
    
    // Load native library to ensure JNI symbols are available
    try {
      System.loadLibrary("BridgeApp")
    } catch (e: UnsatisfiedLinkError) {
      Timber.e(e, "Failed to load BridgeApp library")
    }
    
    // Initialize AndroidChipPlatform (required for ChipAppServer)
    Timber.d("Initializing AndroidChipPlatform...")

    // Inject missing factory data to prevent crash
    // Note: PreferencesConfigurationManager uses "chip.platform.ConfigurationManager" as the file name
    val factoryPrefs = getSharedPreferences("chip.platform.ConfigurationManager", MODE_PRIVATE)
    if (!factoryPrefs.contains("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_DeviceTypeId}")) {
      Timber.d("Injecting factory data...")
      factoryPrefs.edit()
        .putLong("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_DeviceTypeId}", 22L) // Root Node (0x0016)
        .putLong("${ConfigurationManager.kConfigNamespace_ChipFactory}:vendor-id", 65521L)   // 0xFFF1 - VendorId not in Java constants
        .putLong("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_ProductId}", 32769L)  // 0x8001
        .putLong("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_HardwareVersion}", 1L)
        .putString("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_HardwareVersionString}", "1.0")
        .putLong("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_ManufacturingDate}", 20230101L)
        .putString("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_DeviceName}", "Matter Bridge")
        .putString("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_SerialNum}", "TEST_SN_12345678")
        .putString("${ConfigurationManager.kConfigNamespace_ChipFactory}:${ConfigurationManager.kConfigKey_UniqueId}", "TEST_UID_12345678")
        .putLong("${ConfigurationManager.kConfigNamespace_ChipConfig}:${ConfigurationManager.kConfigKey_RegulatoryLocation}", 0L) // Indoor
        .apply()
    }

    androidChipPlatform = AndroidChipPlatform(
      AndroidBleManager(),
      AndroidNfcCommissioningManager(),
      PreferencesKeyValueStoreManager(this),
      PreferencesConfigurationManager(this),
      NsdManagerServiceResolver(this),
      NsdManagerServiceBrowser(this),
      ChipMdnsCallbackImpl(),
      DiagnosticDataProviderImpl(this)
    )
    
    // Initialize CommissionableDataProvider with default values
    // This is required to prevent crash in ChipAppServer.startAppWithDelegate
    // which calls LogDeviceConfig() -> GetCommissionableDataProvider()
    androidChipPlatform?.updateCommissionableDataProviderData(
      null, // spake2pVerifierBase64
      null, // Spake2pSaltBase64
      0,    // spake2pIterationCount
      20202021L, // setupPasscode
      3840  // discriminator
    )
    
    Timber.d("Bridge App initialized - AndroidChipPlatform ready")
  }
}
