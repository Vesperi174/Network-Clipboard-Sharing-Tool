package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * HISTORY命令处理器
 * 负责处理客户端的历史记录请求
 */
public class HistoryCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public HistoryCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        System.out.println("[Server] HISTORY request from " + clientAddr);
        
        // 将历史记录转换为JSON格式
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        var history = historyManager.getHistory();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(",");
            String item = history.get(i);
            // 转义特殊字符
            item = item.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(item).append("\"");
        }
        sb.append("]");
        String historyJson = sb.toString();
        
        byte[] historyResponse = Protocol.createOkMessage(historyJson);
        outputStream.write(historyResponse);
        outputStream.flush();
    }
}