package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 命令处理器接口
 * 应用策略模式，为不同类型的命令提供统一的处理接口
 */
public interface CommandHandler {
    /**
     * 处理命令
     * @param inputStream 输入流
     * @param outputStream 输出流
     * @param clientAddr 客户端地址
     * @param message 协议消息
     * @throws IOException IO异常
     */
    void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException;
}