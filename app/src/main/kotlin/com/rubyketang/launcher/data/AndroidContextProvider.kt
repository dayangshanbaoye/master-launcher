package com.rubyketang.launcher.data

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import com.rubyketang.launcher.engine.rank.ContextProvider
import com.rubyketang.launcher.engine.rank.ContextSignals
import com.rubyketang.launcher.engine.rank.TimeSlot

/**
 * P1-5 设备信号来源。读不到的信号返回 null（不参与计分），不为此弹权限。
 * 车载蓝牙需要 BLUETOOTH_CONNECT（Android 12+ 的运行时权限），未授权时降级为 null。
 */
class AndroidContextProvider(private val context: Context) : ContextProvider {

    override fun current(): ContextSignals = ContextSignals(
        slot = TimeSlot.of(System.currentTimeMillis()),
        headsetConnected = headset(),
        charging = charging(),
        carBluetoothConnected = carBluetooth(),
    )

    private fun charging(): Boolean {
        val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun headset(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        return audio.isWiredHeadsetOn || audio.isBluetoothA2dpOn
    }

    private fun carBluetooth(): Boolean? {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null // 未授权：信号缺席，不打扰用户
        }
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val adapter = manager.adapter ?: return null
        return try {
            adapter.bondedDevices?.any { device ->
                device.bluetoothClass?.let {
                    it.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO &&
                        it.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
                } == true
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
