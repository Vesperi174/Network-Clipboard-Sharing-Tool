package com.clipboard.client;

import com.clipboard.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * 网络剪贴板共享工具 - 客户端
 * <p>
 * 连接服务端，通过命令行交互执行 push/pull/exit 命令。
 */
public class ClipboardClient {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    /**
     * 连接服务端
     *
     * @param host 服务端 IP 地址
     * @param port 服务端端口号
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
        System.out.println("[Client] Connected to server " + host + ":" + port);
    }

    /**
     * 推送文本到服务端
     *
     * @param text 要推送的文本
     */
    public void push(String text) throws IOException {
        byte[] pushMsg = Protocol.pack(Protocol.CMD_PUSH, text);
        out.write(pushMsg);
        out.flush();

        byte[] responseHeader = new byte[5];
        in.readFully(responseHeader);
        Protocol.Message response = Protocol.unpack(responseHeader);

        int dataLength = ((responseHeader[1] & 0xFF) << 24)
                | ((responseHeader[2] & 0xFF) << 16)
                | ((responseHeader[3] & 0xFF) << 8)
                | (responseHeader[4] & 0xFF);
        if (dataLength > 0) {
            byte[] dataBytes = new byte[dataLength];
            in.readFully(dataBytes);
            byte[] fullResponse = new byte[5 + dataLength];
            System.arraycopy(responseHeader, 0, fullResponse, 0, 5);
            System.arraycopy(dataBytes, 0, fullResponse, 5, dataLength);
            response = Protocol.unpack(fullResponse);
        }

        if (response.getCmd() == Protocol.CMD_OK) {
            System.out.println("[Client] PUSH success, " + text.getBytes("UTF-8").length + " bytes sent");
        } else {
            System.out.println("[Client] PUSH failed: " + response.getData());
        }
    }

    /**
     * 从服务端拉取最新文本
     *
     * @return 服务端返回的文本
     */
    public String pull() throws IOException {
        byte[] pullMsg = Protocol.pack(Protocol.CMD_PULL);
        out.write(pullMsg);
        out.flush();

        byte[] responseHeader = new byte[5];
        in.readFully(responseHeader);
        Protocol.Message response = Protocol.unpack(responseHeader);

        int dataLength = ((responseHeader[1] & 0xFF) << 24)
                | ((responseHeader[2] & 0xFF) << 16)
                | ((responseHeader[3] & 0xFF) << 8)
                | (responseHeader[4] & 0xFF);
        String text = "";
        if (dataLength > 0) {
            byte[] dataBytes = new byte[dataLength];
            in.readFully(dataBytes);
            text = new String(dataBytes, "UTF-8");
        }

        if (response.getCmd() == Protocol.CMD_OK) {
            System.out.println("[Client] PULL success, received " + text.getBytes("UTF-8").length + " bytes");
            System.out.println("[Client] Received: " + text);
        } else {
            System.out.println("[Client] PULL failed: " + response.getData());
        }
        return text;
    }

    /**
     * 交互式命令行
     */
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter commands: push / pull / exit");
        while (true) {
            try {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                switch (line.toLowerCase()) {
                    case "push":
                        System.out.print("Enter text to push: ");
                        String text = scanner.nextLine();
                        push(text);
                        break;

                    case "pull":
                        pull();
                        break;

                    case "exit":
                        close();
                        System.out.println("[Client] Disconnected");
                        return;

                    default:
                        System.out.println("Unknown command: " + line + ". Available: push / pull / exit");
                        break;
                }
            } catch (IOException e) {
                System.err.println("[Client] Error: " + e.getMessage());
                break;
            }
        }
        scanner.close();
    }

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java com.clipboard.client.ClipboardClient <host> <port>");
            System.out.println("Example: java com.clipboard.client.ClipboardClient 127.0.0.1 8888");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        ClipboardClient client = new ClipboardClient();
        try {
            client.connect(host, port);
            client.runInteractive();
        } catch (IOException e) {
            System.err.println("[Client] Failed to connect: " + e.getMessage());
        } finally {
            client.close();
        }
    }
}