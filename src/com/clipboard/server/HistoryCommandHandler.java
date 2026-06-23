package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * HISTORY命令处理器
 * 负责处理客户端的历史记录请求
 * 返回JSON格式：[{"user":"...","text":"..."},...]
 */
public class HistoryCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public HistoryCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public boolean handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        SimpleLogger.info("Processing HISTORY request from " + clientAddr);
        System.out.println("[Server] HISTORY request from " + clientAddr);

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        var history = historyManager.getHistory();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(",");
            ClipboardHistoryEntry entry = history.get(i);
            String escapedUser = entry.getUser().replace("\\", "\\\\").replace("\"", "\\\"");
            String escapedText = entry.getText().replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"user\":\"").append(escapedUser).append("\",\"text\":\"").append(escapedText).append("\"}");
        }
        sb.append("]");
        String historyJson = sb.toString();

        byte[] historyResponse = Protocol.createOkMessage(historyJson);
        outputStream.write(historyResponse);
        outputStream.flush();

        SimpleLogger.networkOperation("HISTORY_RESPONSE", "Sent history response to " + clientAddr + ", items count: " + history.size());
        return false;
    }
}