/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.common.util;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringJoiner;

/**
 * 文件相关的工具类
 */
public class FileUtil {

    /**
     * 每次读取多少字节
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * 不允许创建实例
     */
    private FileUtil() {

    }

    /**
     * 文件路径拼接
     *
     * @param strings 要拼接的文本
     * @return 文件路径
     */
    public static String getFilePath(String... strings) {
        StringJoiner sj = new StringJoiner(File.separator);
        for (String string : strings) {
            sj.add(string);
        }
        return sj.toString();
    }

    /**
     * 文件路径拼接
     *
     * @param strings 要拼接的文本
     * @return 文件
     */
    @SuppressWarnings("unused")
    public static File getFile(String... strings) {
        return new File(getFilePath(strings));
    }

    /**
     * 文件路径拼接
     *
     * @param file    文件
     * @param strings 要拼接的文本
     * @return 文件
     */
    @SuppressWarnings("unused")
    public static File getFile(File file, String... strings) {
        return getFile(file.getPath(), getFilePath(strings));
    }

    /**
     * 创建文件所在的文件夹
     *
     * @param file 文件
     * @return 文件所在的文件夹是否存在
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean createParentFile(File file) {
        if (file.exists()) {
            return true;
        }
        File parent = file.getParentFile();
        return parent == null || parent.exists() || parent.mkdirs();
    }

    /**
     * 把字符串写入到文本中
     *
     * @param file 文件
     * @param str  字符串
     * @return 是否成功
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean writeString(File file, String str) {
        if (!createParentFile(file)) {
            return false;
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(str.getBytes());
            return true;
        } catch (IOException e) {
            MonitorUtil.generateCustomLog(e, "IOException");
            return false;
        }
    }

    /**
     * 从文件中读取字符串
     *
     * @param file 文件
     * @return 读取的字符串
     */
    @Nullable
    public static String readString(File file) {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            return new String(readAllByte(inputStream));
        } catch (IOException e) {
            MonitorUtil.generateCustomLog(e, "IOException");
            return null;
        }
    }

    /**
     * 复制所有内容
     *
     * @param inputStream  输入流
     * @param outputStream 输出流
     * @return 复制了多少字节
     * @throws IOException IO错误
     */
    @SuppressWarnings("UnusedReturnValue")
    public static int copyAllBytes(InputStream inputStream, OutputStream outputStream) throws IOException {
        int byteCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            int read = inputStream.read(buffer);
            if (read == -1) {
                break;
            }
            outputStream.write(buffer, 0, read);
            byteCount += read;
        }
        return byteCount;
    }

    /**
     * 读取全部字节
     *
     * @param inputStream 输入流
     * @return 读取的数据列表
     * @throws IOException IO错误
     */
    public static byte[] readAllByte(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            copyAllBytes(inputStream, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }
}
