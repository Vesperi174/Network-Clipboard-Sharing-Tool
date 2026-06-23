package com.clipboard.server;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络剪贴板共享工具 - 服务端
 * <p>
 * 监听指定端口，接受客户端连接，处理 PUSH/PULL/HISTORY/DELETE 请求。
 * 每个客户端连接在独立线程中处理。
 * 应用策略模式、工厂模式和观察者模式优化命令处理逻辑。
 * 当 PUSH/DELETE 操作成功后，服务端主动推送刷新通知给所有在线客户端。
 */
public class ClipboardServer {
    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_THREADS = 50;
    
    private final ClipboardHistoryManager historyManager;
    private final Map<Byte, CommandHandler> commandHandlers;
    private final ClientManager clientManager;
    private final ExecutorService threadPool;
    private final UnknownCommandHandler unknownHandler;
    
    public ClipboardServer() {
        this.historyManager = new ClipboardHistoryManager();
        this.commandHandlers = initializeCommandHandlers();
        this.clientManager = new ClientManager();
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ClientHandler");
            t.setDaemon(true);
            return t;
        });
        this.unknownHandler = new UnknownCommandHandler();
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
                threadPool.submit(() -> handleClient(clientSocket, clientAddr));
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
        DataOutputStream out = null;
        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            clientManager.register(clientAddr, out);

            SimpleLogger.info("Client session started for " + clientAddr);
            byte[] header = new byte[5];
            
            while (true) {
                in.readFully(header);
                
                int dataLength = ((header[1] & 0xFF) << 24)
                        | ((header[2] & 0xFF) << 16)
                        | ((header[3] & 0xFF) << 8)
                        | (header[4] & 0xFF);
                byte[] dataBytes = new byte[dataLength];
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

                CommandHandler handler = commandHandlers.getOrDefault(
                    fullMessage.getCmd(), 
                    unknownHandler
                );
                boolean shouldBroadcast = handler.handle(in, out, clientAddr, fullMessage);
                
                if (shouldBroadcast) {
                    byte[] notifyMsg = Protocol.createNotifyRefreshMessage();
                    clientManager.broadcast(notifyMsg, clientAddr);
                    SimpleLogger.info("Broadcasted NOTIFY_REFRESH to all clients after " + 
                        fullMessage.getCmdName() + " from " + clientAddr);
                }
            }
        } catch (IOException e) {
            SimpleLogger.connectionStatus("CONNECTION_CLOSED", "Connection closed: " + clientAddr);
            System.out.println("[Server] Connection closed: " + clientAddr);
        } finally {
            if (out != null) {
                clientManager.unregister(clientAddr);
            }
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

    /**
     * 客户端管理器
     * 维护所有在线客户端的输出流，支持广播通知
     */
    private static class ClientManager {
        private final List<ClientEntry> clients = new CopyOnWriteArrayList<>();

        private static class ClientEntry {
            final String addr;
            final DataOutputStream out;

            ClientEntry(String addr, DataOutputStream out) {
                this.addr = addr;
                this.out = out;
            }
        }

        void register(String addr, DataOutputStream out) {
            clients.add(new ClientEntry(addr, out));
            SimpleLogger.info("Client registered for broadcast: " + addr + ", total clients: " + clients.size());
        }

        void unregister(String addr) {
            clients.removeIf(e -> e.addr.equals(addr));
            SimpleLogger.info("Client unregistered from broadcast: " + addr + ", total clients: " + clients.size());
        }

        void broadcast(byte[] message, String excludeAddr) {
            for (ClientEntry entry : clients) {
                if (entry.addr.equals(excludeAddr)) {
                    continue;
                }
                try {
                    synchronized (entry.out) {
                        entry.out.write(message);
                        entry.out.flush();
                    }
                } catch (IOException e) {
                    SimpleLogger.warn("Failed to broadcast to " + entry.addr + ": " + e.getMessage());
                }
            }
        }
    }
}