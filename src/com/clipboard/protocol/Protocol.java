package com.clipboard.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 协议编解码工具类
 * <p>
 * 消息格式：1字节命令字 + 4字节数据长度(大端) + N字节数据内容(UTF-8)
 * 应用工厂模式创建消息
 */
public class Protocol {

    public static final byte CMD_PUSH = 1;
    public static final byte CMD_PULL = 2;
    public static final byte CMD_OK = 3;
    public static final byte CMD_ERROR = 4;
    public static final byte CMD_HISTORY = 5;

    private Protocol() {
        // 工具类不允许实例化
    }

    /**
     * 创建PUSH消息
     * @param data 要推送的数据
     * @return 消息字节数组
     */
    public static byte[] createPushMessage(String data) {
        return pack(CMD_PUSH, data);
    }

    /**
     * 创建PULL消息
     * @return 消息字节数组
     */
    public static byte[] createPullMessage() {
        return pack(CMD_PULL);
    }

    /**
     * 创建HISTORY消息
     * @return 消息字节数组
     */
    public static byte[] createHistoryMessage() {
        return pack(CMD_HISTORY);
    }

    /**
     * 创建OK响应消息
     * @param data 响应数据
     * @return 消息字节数组
     */
    public static byte[] createOkMessage(String data) {
        return pack(CMD_OK, data);
    }

    /**
     * 创建错误响应消息
     * @param errorMessage 错误信息
     * @return 消息字节数组
     */
    public static byte[] createErrorMessage(String errorMessage) {
        return pack(CMD_ERROR, errorMessage);
    }

    /**
     * 打包消息：命令字 + 数据
     *
     * @param cmd  命令字
     * @param data 数据内容
     * @return 完整的消息字节数组
     */
    private static byte[] pack(byte cmd, String data) {
        try {
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeByte(cmd);
            dos.writeInt(dataBytes.length);
            dos.write(dataBytes);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to pack message", e);
        }
    }
    
    /**
     * 打包消息：命令字 + 空数据
     *
     * @param cmd 命令字
     * @return 完整的消息字节数组（仅命令字+长度0）
     */
    private static byte[] pack(byte cmd) {
        return pack(cmd, "");
    }

    /**
     * 解包消息
     *
     * @param rawData 完整的消息字节数组（5 + N 字节）
     * @return Message 对象，包含命令字和数据
     */
    public static Message unpack(byte[] rawData) {
        if (rawData.length < 5) {
            throw new IllegalArgumentException("Invalid message: too short, length=" + rawData.length);
        }
        byte cmd = rawData[0];
        int length = ((rawData[1] & 0xFF) << 24)
                | ((rawData[2] & 0xFF) << 16)
                | ((rawData[3] & 0xFF) << 8)
                | (rawData[4] & 0xFF);
        if (rawData.length < 5 + length) {
            throw new IllegalArgumentException("Invalid message: data length mismatch");
        }
        String data = new String(rawData, 5, length, StandardCharsets.UTF_8);
        return new Message(cmd, data);
    }

    /**
     * 消息实体类，使用不可变对象模式
     */
    public static class Message {
        private final byte cmd;
        private final String data;

        public Message(byte cmd, String data) {
            this.cmd = cmd;
            this.data = data;
        }

        public byte getCmd() {
            return cmd;
        }

        public String getData() {
            return data;
        }

        public String getCmdName() {
            switch (cmd) {
                case CMD_PUSH:
                    return "PUSH";
                case CMD_PULL:
                    return "PULL";
                case CMD_OK:
                    return "OK";
                case CMD_ERROR:
                    return "ERROR";
                case CMD_HISTORY:
                    return "HISTORY";
                default:
                    return "UNKNOWN(" + cmd + ")";
            }
        }
        
        public boolean isSuccessful() {
            return cmd == CMD_OK;
        }
        
        public boolean isError() {
            return cmd == CMD_ERROR;
        }
    }
}