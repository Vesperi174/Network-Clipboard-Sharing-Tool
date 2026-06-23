package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * 网络剪贴板共享工具 - 服务端
 * <p>
 * 监听指定端口，接受客户端连接，处理 PUSH/PULL/HISTORY 请求。
 * 每个客户端连接在独立线程中处理。
 * 应用策略模式和工厂模式优化命令处理逻辑
 */
public class ClipboardServer {
    private static final int DEFAULT_PORT = 8888;
    
    private final ClipboardHistoryManager historyManager;
    private final Map<Byte, CommandHandler> commandHandlers;
    
    public ClipboardServer() {
        this.historyManager = new ClipboardHistoryManager();
        this.commandHandlers = initializeCommandHandlers();
    }

    /**
     * 初始化命令处理器映射
     */
    private Map<Byte, CommandHandler> initializeCommandHandlers() {
        Map<Byte, CommandHandler> handlers = new HashMap<>();
        handlers.put(Protocol.CMD_PUSH, new PushCommandHandler(historyManager));
        handlers.put(Protocol.CMD_PULL, new PullCommandHandler(historyManager));
        handlers.put(Protocol.CMD_HISTORY, new HistoryCommandHandler(historyManager));
        handlers.put(Protocol.CMD_DELETE, new DeleteCommandHandler(historyManager));
        return handlers;
    }

    /**
     * 启动服务端，监听默认端口
     */
    public void start() {
        start(DEFAULT_PORT);
    }

    /**
     * 启动服务端，监听指定端口
     *
     * @param port 监听端口号
     */
    public void start(int port) {
        SimpleLogger.applicationEvent("SERVER_START", "Starting clipboard server on port " + port);
        System.out.println("[Server] Listening on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            SimpleLogger.info("Server socket bound successfully on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddr = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                SimpleLogger.connectionStatus("CLIENT_CONNECTED", "New connection from " + clientAddr);
                System.out.println("[Server] New connection from " + clientAddr);
                new Thread(() -> handleClient(clientSocket, clientAddr)).start();
            }
        } catch (IOException e) {
            SimpleLogger.error("Failed to start server on port " + port, e);
            System.err.println("[Server] Failed to start server: " + e.getMessage());
        }
    }

    /**
     * 处理单个客户端连接
     *
     * @param clientSocket 客户端 Socket
     * @param clientAddr   客户端地址标识
     */
    private void handleClient(Socket clientSocket, String clientAddr) {
        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            SimpleLogger.info("Client session started for " + clientAddr);
            
            while (true) {
                byte[] header = new byte[5];
                in.readFully(header);
                
                int dataLength = ((header[1] & 0xFF) << 24)
                        | ((header[2] & 0xFF) << 16)
                        | ((header[3] & 0xFF) << 8)
                        | (header[4] & 0xFF);
                byte[] dataBytes = new byte[dataLength]; // Always allocate the exact size needed
                if (dataLength > 0) {
                    in.readFully(dataBytes);
                }

                byte[] fullMsg = new byte[5 + dataLength];
                System.arraycopy(header, 0, fullMsg, 0, 5);
                System.arraycopy(dataBytes, 0, fullMsg, 5, dataLength);
                Protocol.Message fullMessage = Protocol.unpack(fullMsg);

                SimpleLogger.dataTransfer("INCOMING", "COMMAND", fullMsg.length, 
                    "Received command from " + clientAddr + ", cmd: " + fullMessage.getCmd() + 
                    ", data length: " + dataLength);

                // 使用策略模式处理命令
                CommandHandler handler = commandHandlers.getOrDefault(
                    fullMessage.getCmd(), 
                    new UnknownCommandHandler()
                );
                handler.handle(in, out, clientAddr, fullMessage);
            }
        } catch (IOException e) {
            SimpleLogger.connectionStatus("CONNECTION_CLOSED", "Connection closed: " + clientAddr);
            System.out.println("[Server] Connection closed: " + clientAddr);
        } finally {
            try {
                clientSocket.close();
                SimpleLogger.info("Client socket closed for " + clientAddr);
            } catch (IOException ignored) {
                SimpleLogger.debug("Exception ignored when closing client socket for " + clientAddr);
            }
        }
    }

    public static void main(String[] args) {
        SimpleLogger.init("clipboard_server.log");
        ClipboardServer server = new ClipboardServer();
        if (args.length > 0) {
            int port = Integer.parseInt(args[0]);
            server.start(port);
        } else {
            server.start();
        }
    }
}