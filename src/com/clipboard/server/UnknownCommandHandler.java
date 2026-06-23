package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 未知命令处理器
 * 负责处理未知的命令请求
 */
public class UnknownCommandHandler implements CommandHandler {
    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        byte[] errorResponse = Protocol.createErrorMessage("Unknown command: " + message.getCmd());
        outputStream.write(errorResponse);
        outputStream.flush();
    }
}