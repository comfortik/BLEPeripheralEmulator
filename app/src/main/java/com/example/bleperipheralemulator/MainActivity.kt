package com.example.bleperipheralemulator

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import java.util.*

class MainActivity : Activity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private lateinit var characteristic: BluetoothGattCharacteristic

    companion object {
        private const val TAG = "MainActivity"
        private val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val BLOCK_SIZE = 160
        private const val INTERVAL_MS = 60

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Убедитесь, что у вас есть layout файл

        Log.d(TAG, "Initialization started")

        // Получаем BluetoothManager и адаптер
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        // Инициализация и запуск
        bluetoothAdapter.name = "MyBLEDevice"
        runOnUiThread {
            try {
                setupGattServer()
                startAdvertising()
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
            }
        }
    }

    private fun setupGattServer() {
        try {
            bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            val descriptor = BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(descriptor)

            service.addCharacteristic(characteristic)
            bluetoothGattServer.addService(service)
        } catch (e: Exception) {
            Log.e(TAG, "Setup GATT server failed", e)
        }
    }


    private fun startAdvertising() {
        Log.d(TAG, "Starting advertising")
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }



    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error code: $errorCode")
        }
    }



    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "Connection state changed: $status, $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device.address}")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            Log.i(TAG, "Read request for characteristic: ${characteristic.uuid}")
            if (CHARACTERISTIC_UUID == characteristic.uuid) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
            } else {
                Log.e(TAG, "Read request for unknown characteristic: ${characteristic.uuid}")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            Log.i(TAG, "Write request for characteristic: ${characteristic.uuid}")
            if (CHARACTERISTIC_UUID == characteristic.uuid) {
                characteristic.value = value
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                runOnUiThread {
                    val tv = findViewById<TextView>(R.id.tv)
                    tv.text = value.toString(Charsets.UTF_8)
                }
                Log.i(TAG, "Characteristic written successfully")
            } else {
                Log.e(TAG, "Write request for unknown characteristic: ${characteristic.uuid}")
            }
        }



    }

}
