package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * PUSH命令处理器
 * 负责处理客户端的PUSH请求
 */
public class PushCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public PushCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        System.out.println("[Server] PUSH from " + clientAddr + ", length=" + message.getData().length());
        historyManager.addHistory(message.getData());
        
        byte[] okResponse = Protocol.createOkMessage("");
        outputStream.write(okResponse);
        outputStream.flush();
    }
}