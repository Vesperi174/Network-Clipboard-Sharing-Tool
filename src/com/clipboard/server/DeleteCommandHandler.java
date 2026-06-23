package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * DELETE命令处理器
 * 负责处理客户端删除历史记录的请求
 */
public class DeleteCommandHandler implements CommandHandler {
    private final ClipboardHistoryManager historyManager;

    public DeleteCommandHandler(ClipboardHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public void handle(DataInputStream inputStream, DataOutputStream outputStream, String clientAddr, Protocol.Message message) throws IOException {
        String indexStr = message.getData();
        SimpleLogger.info("Processing DELETE request from " + clientAddr + ", index: " + indexStr);
        System.out.println("[Server] DELETE from " + clientAddr + ", index=" + indexStr);

        try {
            int index = Integer.parseInt(indexStr);
            if (historyManager.deleteEntry(index)) {
                byte[] okResponse = Protocol.createOkMessage("");
                outputStream.write(okResponse);
                outputStream.flush();
                SimpleLogger.networkOperation("DELETE_RESPONSE", "Sent OK response to " + clientAddr);
            } else {
                byte[] errorResponse = Protocol.createErrorMessage("Invalid index");
                outputStream.write(errorResponse);
                outputStream.flush();
                SimpleLogger.error("DELETE failed: invalid index " + index + " from " + clientAddr);
            }
        } catch (NumberFormatException e) {
            byte[] errorResponse = Protocol.createErrorMessage("Invalid index format");
            outputStream.write(errorResponse);
            outputStream.flush();
            SimpleLogger.error("DELETE failed: invalid index format from " + clientAddr);
        }
    }
}