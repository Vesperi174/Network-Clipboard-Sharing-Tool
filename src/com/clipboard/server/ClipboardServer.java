package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 网络剪贴板共享工具 - 服务端
 * <p>
 * 监听指定端口，接受客户端连接，处理 PUSH/PULL 请求。
 * 每个客户端连接在独立线程中处理。
 */
public class ClipboardServer {

    private static final int DEFAULT_PORT = 8888;
    private String latestText = "";
    private final List<String> history = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY_SIZE = 50;

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
        System.out.println("[Server] Listening on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddr = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                System.out.println("[Server] New connection from " + clientAddr);
                new Thread(() -> handleClient(clientSocket, clientAddr)).start();
            }
        } catch (IOException e) {
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

                switch (fullMessage.getCmd()) {
                    case Protocol.CMD_PUSH:
                        System.out.println("[Server] PUSH from " + clientAddr + ", length=" + fullMessage.getData().length());
                        synchronized (this) {
                            latestText = fullMessage.getData();
                            history.add(0, fullMessage.getData()); // Add to front of history
                            if (history.size() > MAX_HISTORY_SIZE) {
                                history.remove(history.size() - 1); // Remove oldest entry if exceeding max size
                            }
                        }
                        byte[] okResponse = Protocol.pack(Protocol.CMD_OK);
                        out.write(okResponse);
                        out.flush();
                        break;

                    case Protocol.CMD_PULL:
                        System.out.println("[Server] PULL from " + clientAddr);
                        String text;
                        synchronized (this) {
                            text = latestText;
                        }
                        byte[] pullResponse = Protocol.pack(Protocol.CMD_OK, text);
                        out.write(pullResponse);
                        out.flush();
                        break;

                    case Protocol.CMD_HISTORY:
                        System.out.println("[Server] HISTORY request from " + clientAddr);
                        String historyJson;
                        synchronized (this) {
                            // Convert history list to JSON string
                            StringBuilder sb = new StringBuilder();
                            sb.append("[");
                            for (int i = 0; i < history.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append("\"").append(history.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                            }
                            sb.append("]");
                            historyJson = sb.toString();
                        }
                        byte[] historyResponse = Protocol.pack(Protocol.CMD_OK, historyJson);
                        out.write(historyResponse);
                        out.flush();
                        break;

                    default:
                        byte[] errorResponse = Protocol.pack(Protocol.CMD_ERROR, "Unknown command: " + fullMessage.getCmd());
                        out.write(errorResponse);
                        out.flush();
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] Connection closed: " + clientAddr);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        ClipboardServer server = new ClipboardServer();
        if (args.length > 0) {
            int port = Integer.parseInt(args[0]);
            server.start(port);
        } else {
            server.start();
        }
    }
}