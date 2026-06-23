package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * PUSH命令处理器
 * 负责处理客户端的PUSH请求
 * 数据格式：username\ncontent
 */
public class PushCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public PushCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        String rawData = message.getData();
        int sepIndex = rawData.indexOf('\n');
        String username;
        String textData;
        if (sepIndex >= 0) {
            username = rawData.substring(0, sepIndex);
            textData = rawData.substring(sepIndex + 1);
        } else {
            username = "anonymous";
            textData = rawData;
        }

        SimpleLogger.info("Processing PUSH request from " + clientAddr + " (user: " + username + "), text length: " + textData.length());
        System.out.println("[Server] PUSH from " + username + " (" + clientAddr + "), length=" + textData.length());

        historyManager.addHistory(username, textData);

        byte[] okResponse = Protocol.createOkMessage("");
        outputStream.write(okResponse);
        outputStream.flush();

        SimpleLogger.networkOperation("PUSH_RESPONSE", "Sent OK response to " + clientAddr);
    }
}