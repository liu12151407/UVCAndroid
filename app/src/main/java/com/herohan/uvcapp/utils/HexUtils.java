package com.herohan.uvcapp.utils;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

public class HexUtils {

    /**
     * 十六进制字符串转换byte数组
     */
    public static byte[] hexStringToByteArray(String digits) {
        String s = digits.length() % 2 > 0 ? "0" + digits : digits;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i += 2) {
            char c1 = s.charAt(i);
            if ((i + 1) >= s.length()) {
                throw new IllegalArgumentException("hexUtil.odd");
            }
            char c2 = s.charAt(i + 1);
            byte b = 0;
            if ((c1 >= '0') && (c1 <= '9')) {
                b += ((c1 - '0') * 16);
            } else if ((c1 >= 'a') && (c1 <= 'f')) {
                b += ((c1 - 'a' + 10) * 16);
            } else if ((c1 >= 'A') && (c1 <= 'F')) {
                b += ((c1 - 'A' + 10) * 16);
            } else {
                throw new IllegalArgumentException("hexUtil.bad");
            }

            if ((c2 >= '0') && (c2 <= '9')) {
                b += (c2 - '0');
            } else if ((c2 >= 'a') && (c2 <= 'f')) {
                b += (c2 - 'a' + 10);
            } else if ((c2 >= 'A') && (c2 <= 'F')) {
                b += (c2 - 'A' + 10);
            } else {
                throw new IllegalArgumentException("hexUtil.bad");
            }
            baos.write(b);
        }
        return (baos.toByteArray());
    }


    /**
     * byte数组 转换为 十六进制 字符串
     */

    public static String byteArrayToHexStr(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(digitToHexChar((bytes[i] >> 4)));
            sb.append(digitToHexChar((bytes[i] & 0x0f)));
        }
        return (sb.toString());
    }

    /**
     * 数字到十六进制字符
     *
     * @param value
     * @return
     */
    private static char digitToHexChar(int value) {
        value = value & 0x0f;
        if (value >= 10) {
            return ((char) (value - 10 + 'A'));
        } else {
            return ((char) (value + '0'));
        }
    }

    /**
     * 16进制字符串转换为10进制数字
     *
     * @param hexs
     * @return
     */
    public static int decodeHEX(String hexs) {
        BigInteger bigint = new BigInteger(hexs, 16);
        int numb = bigint.intValue();
        return numb;
    }


    /**
     * 10进制数字转换为16进制字符串
     *
     * @param numb
     * @return
     */
    public static String encodeHEX(Integer numb) {
        String hex = Integer.toHexString(numb);
        return hex;
    }

    /**
     * 10进制数字转换为16进制字符串 1位时 补0
     *
     * @param n
     * @return
     */
    public static String getHexStr1B(Integer n) {
        String number = Integer.toHexString(n);
        if (number.length() == 1) {
            number = "0" + number;
        }
        return number;
    }

    /**
     * 10进制数字转换为16进制字符串 2位时 补0
     *
     * @param n
     * @return
     */
    public static String getHexStr2B(Integer n) {
        String number = Integer.toHexString(n);
        switch (number.length()) {
            case 1:
                number = "000" + number;
                break;
            case 2:
                number = "00" + number;
                break;
            case 3:
                number = "0" + number;
                break;
            default:
                break;
        }
        return number;
    }

    /**
     * 10进制数字转换为16进制字符串 4位时 补0
     *
     * @param n
     * @return
     */
    public static String getHexStr4B(Integer n) {
        String number = Integer.toHexString(n);
        switch (number.length()) {
            case 1:
                number = "0000000" + number;
                break;
            case 2:
                number = "000000" + number;
                break;
            case 3:
                number = "00000" + number;
                break;
            case 4:
                number = "0000" + number;
                break;
            case 5:
                number = "000" + number;
                break;
            case 6:
                number = "00" + number;
                break;
            case 7:
                number = "0" + number;
                break;
            default:
                break;
        }
        return number;
    }

    /**
     * java 合并多个byte[]为一个byte数组
     *
     * @param values
     * @return
     */
    public static byte[] byteMergerAll(byte[]... values) {
        int lengthByte = 0;
        for (int i = 0; i < values.length; i++) {
            lengthByte += values[i].length;
        }
        byte[] allByte = new byte[lengthByte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, allByte, countLength, b.length);
            countLength += b.length;
        }
        return allByte;
    }
}
