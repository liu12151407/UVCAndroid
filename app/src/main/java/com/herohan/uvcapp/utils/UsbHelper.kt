package com.herohan.uvcapp.utils

import android.content.Context
import android.hardware.usb.UsbManager
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber


/**
 * ***********************************************
 * 包路径：com.ut.usbserial.helper
 * 类描述：usb帮助类
 * 创建人：Liu Yinglong[PHONE：13281160095]
 * 创建时间：2022/11/07
 * 修改人：
 * 修改时间：2022/11/07
 * 修改备注：
 * ***********************************************
 */
class UsbHelper private constructor() {
    private val tag = "==UsbHelper=="

    /**
     * 双重校验锁式（Double Check)
     */
    companion object {
        val instance: UsbHelper by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            UsbHelper()
        }
    }

    /**
     * usb缓存数据
     */
    val spu = SPUtils.getInstance("UsbHelper")

    /**
     * 设备管理器
     */
    private var manager: UsbManager? = null

    /**
     * 设备集合
     */
    fun availableDrivers(ctx: Context): MutableList<UsbSerialDriver> {
        manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    }

    /**
     * 当前选中的usb设备
     */
    var driver: UsbSerialDriver? = null
    var port: UsbSerialPort? = null

    /**
     * 打开串口
     */
    fun open(): Boolean {
        val op = port?.isOpen == true
        if (op) {
            ToastUtils.showLong("设备已连接")
            return true
        }
        if (driver == null) {
            ToastUtils.showLong("未找到相应设备")
            return false
        }
        val connection = manager?.openDevice(driver?.device)
        if (connection == null) {
            ToastUtils.showLong("连接失败")
            return false
        }
        port = driver?.ports?.get(0)
        port?.open(connection)
        port?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return port?.isOpen == true
    }

    /**
     * 关闭连接
     */
    fun close() {
        try {
            port?.close()
            port = null
        } catch (e: Exception) {
            LogUtils.e(tag, "异常", e.message)
        }
    }

    /**
     * 发送指令,协议
     */
    fun send(data: ByteArray) {
        try {
            port?.write(data, 50)
        } catch (e: Exception) {
            LogUtils.e(tag, "异常", e.message)
        }
    }
}