package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * PULL命令处理器
 * 负责处理客户端的PULL请求
 */
public class PullCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public PullCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        SimpleLogger.info("Processing PULL request from " + clientAddr);
        System.out.println("[Server] PULL from " + clientAddr);
        String text = historyManager.getLatestContent();
        
        byte[] pullResponse = Protocol.createOkMessage(text);
        outputStream.write(pullResponse);
        outputStream.flush();
        
        SimpleLogger.networkOperation("PULL_RESPONSE", "Sent PULL response to " + clientAddr + ", text length: " + text.length());
    }
}