package com.herohan.uvcapp.utils

/**
 * ***********************************************
 * 包路径：com.ut.usbserial.tools
 * 类描述：
 * 创建人：Liu Yinglong[PHONE：13281160095]
 * 创建时间：2022/11/07
 * 修改人：
 * 修改时间：2022/11/07
 * 修改备注：
 * ***********************************************
 */

const val BITS_OF_BYTE = 8
const val POLYNOMIAL = 0xA001
const val INITIAL_VALUE = 0xFFFF
const val FF = 0xFF

/**
 * crc16 content+crc
 */
fun ByteArray.crc16(): ByteArray {
    var res = INITIAL_VALUE
    for (data in this) {
        res = res xor (data.toInt() and FF)
        for (i in 0 until BITS_OF_BYTE) {
            res = if (res and 0x0001 == 1) res shr 1 xor POLYNOMIAL else res shr 1
        }
    }
    val lowByte: Byte = (res shr 8 and FF).toByte()
    val highByte: Byte = (res and FF).toByte()
    return this.plus(highByte).plus(lowByte)
}

/**
 * crc
 */
fun IntArray.crc16(): ByteArray {
    val byteArray = ByteArray(this.size + 2)
    var res = INITIAL_VALUE
    for (index in this.indices) {
        res = res xor this[index]
        byteArray[index] = this[index].toByte()
        for (i in 0 until BITS_OF_BYTE) {
            res = if (res and 0x0001 == 1) res shr 1 xor POLYNOMIAL else res shr 1
        }
    }
    val lowByte: Byte = (res shr 8 and FF).toByte()
    val highByte: Byte = (res and FF).toByte()
    byteArray[this.size] = highByte
    byteArray[this.size + 1] = lowByte
    return byteArray
}

/**
 * crc合法效验
 */
fun ByteArray.crc16Verify(): Boolean {
    if (this.size < 3) {
        return false
    }
    var res = INITIAL_VALUE
    for (index in 0..this.size - 3) {
        res = res xor (this[index].toInt() and FF)
        for (i in 0 until BITS_OF_BYTE) {
            res = if (res and 0x0001 == 1) res shr 1 xor POLYNOMIAL else res shr 1
        }
    }
    val lowByte: Byte = (res shr 8 and FF).toByte()
    val highByte: Byte = (res and FF).toByte()
    return highByte == this[this.size - 2] && lowByte == this[this.size - 1]
}

/**
 * int转1字节数组
 */
fun Int.to1ByteArray(): ByteArray {
    val digits = HexUtils.getHexStr1B(this)
    return HexUtils.hexStringToByteArray(digits)
}

/**
 * int转2字节数组
 */
fun Int.to2ByteArray(): ByteArray {
    val digits = HexUtils.getHexStr2B(this)
    return HexUtils.hexStringToByteArray(digits)
}

/**
 * int转4字节数组
 */
fun Int.to4ByteArray(): ByteArray {
    val digits = HexUtils.getHexStr4B(this)
    return HexUtils.hexStringToByteArray(digits)
}