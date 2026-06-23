package com.clipboard.client;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 网络剪贴板共享工具 - GUI客户端
 * <p>
 * 带有图形界面的客户端，显示所有用户的历史记录，支持可视化选择复制和删除。
 */
public class GUIClipboardClient extends JFrame {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JPanel historyPanel;
    private JScrollPane historyScrollPane;
    private JButton refreshButton;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;

    private boolean connected = false;
    private String username = "";
    private ExecutorService networkExecutor;
    private Thread readerThread;
    private BlockingQueue<Protocol.Message> responseQueue;
    private volatile boolean running = false;
    private JScrollPane inputScrollPane;

    private final List<HistoryItem> historyItems = new ArrayList<>();

    private static class HistoryItem {
        final String user;
        final String text;
        final int index;

        HistoryItem(String user, String text, int index) {
            this.user = user;
            this.text = text;
            this.index = index;
        }

        boolean isOwn(String myUsername) {
            return user.equals(myUsername);
        }
    }

    public GUIClipboardClient() {
        setTitle("网络剪贴板共享工具 - GUI客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 650);
        setLocationRelativeTo(null);

        networkExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NetworkWorker");
            t.setDaemon(true);
            return t;
        });

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        updateComponentStates();
    }

    private void initializeComponents() {
        hostField = new DarkTextField("127.0.0.1", 12);
        portField = new DarkTextField("8888", 6);
        usernameField = new DarkTextField("", 10);
        connectButton = new DarkButton("连接", true);
        disconnectButton = new DarkButton("断开", false);
        statusLabel = new JLabel("未连接");

        historyPanel = new JPanel();
        historyPanel.setLayout(new GridBagLayout());
        historyPanel.setBackground(new Color(0x1A, 0x1A, 0x2E));
        historyScrollPane = new JScrollPane(historyPanel);
        historyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        historyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        historyScrollPane.setBackground(new Color(0x1A, 0x1A, 0x2E));
        historyScrollPane.getViewport().setBackground(new Color(0x1A, 0x1A, 0x2E));

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputScrollPane = new JScrollPane(inputArea);
        sendButton = new DarkButton("发送到共享剪贴板", true);
        refreshButton = new DarkButton("刷新历史", false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(0x0F, 0x0F, 0x1A));

        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectPanel.setBackground(new Color(0x1A, 0x1A, 0x2E));
        connectPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x55), 1, true),
                "连接设置", TitledBorder.LEFT, TitledBorder.TOP,
                connectPanel.getFont().deriveFont(Font.BOLD, 12f),
                new Color(0xAA, 0xAA, 0xCC)));
        connectPanel.add(new JLabel("主机:"));
        connectPanel.add(hostField);
        connectPanel.add(new JLabel("端口:"));
        connectPanel.add(portField);
        connectPanel.add(new JLabel("用户名:"));
        connectPanel.add(usernameField);
        connectPanel.add(connectButton);
        connectPanel.add(disconnectButton);
        connectPanel.add(statusLabel);

        JPanel historyOuterPanel = new JPanel(new BorderLayout());
        historyOuterPanel.setBackground(new Color(0x1A, 0x1A, 0x2E));
        historyOuterPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x55), 1, true),
                "共享剪贴板历史", TitledBorder.LEFT, TitledBorder.TOP,
                historyOuterPanel.getFont().deriveFont(Font.BOLD, 12f),
                new Color(0xAA, 0xAA, 0xCC)));
        historyOuterPanel.add(historyScrollPane, BorderLayout.CENTER);

        JPanel historyButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        historyButtonPanel.setBackground(new Color(0x1A, 0x1A, 0x2E));
        historyButtonPanel.add(refreshButton);
        historyOuterPanel.add(historyButtonPanel, BorderLayout.SOUTH);

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.setBackground(new Color(0x1A, 0x1A, 0x2E));
        sendPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x33, 0x33, 0x55), 1, true),
                "发送文本", TitledBorder.LEFT, TitledBorder.TOP,
                sendPanel.getFont().deriveFont(Font.BOLD, 12f),
                new Color(0xAA, 0xAA, 0xCC)));
        sendPanel.add(inputScrollPane, BorderLayout.CENTER);
        sendPanel.add(sendButton, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyOuterPanel, sendPanel);
        splitPane.setDividerLocation(400);
        splitPane.setBackground(new Color(0x0F, 0x0F, 0x1A));

        add(connectPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        sendButton.addActionListener(e -> sendText());
        refreshButton.addActionListener(e -> refreshHistory());

        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "sendText");
        inputArea.getActionMap().put("sendText", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!inputArea.getText().isEmpty()) {
                    sendText();
                }
            }
        });
    }

    private void connectToServer() {
        if (connected) {
            SimpleLogger.warn("Attempted to connect while already connected");
            return;
        }

        username = usernameField.getText().trim();
        if (username.isEmpty()) {
            SimpleLogger.warn("Attempted to connect without username");
            JOptionPane.showMessageDialog(this, "请输入用户名！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            SimpleLogger.error("Invalid port number entered: " + portField.getText().trim());
            JOptionPane.showMessageDialog(this, "无效的端口号！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            SimpleLogger.info("Attempting to connect to server " + host + ":" + port + " as user: " + username);
            socket = new Socket(host, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            connected = true;

            if (networkExecutor == null || networkExecutor.isShutdown()) {
                networkExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "NetworkWorker");
                    t.setDaemon(true);
                    return t;
                });
            }

            statusLabel.setText("已连接 - " + username + " @ " + host + ":" + port);
            updateComponentStates();
            startReaderThread();
            refreshHistory();

            SimpleLogger.connectionStatus("CONNECTED", "Successfully connected to " + host + ":" + port + " as " + username);
            JOptionPane.showMessageDialog(this, "成功连接到服务器！", "连接成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            SimpleLogger.error("Failed to connect to server " + host + ":" + port + ", error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "连接失败: " + e.getMessage(), "连接错误", JOptionPane.ERROR_MESSAGE);
            connected = false;
            updateComponentStates();
        }
    }

    private void disconnectFromServer() {
        if (!connected) {
            SimpleLogger.warn("Attempted to disconnect while not connected");
            return;
        }

        try {
            SimpleLogger.info("Initiating disconnection from server");

            running = false;
            if (readerThread != null) {
                readerThread.interrupt();
                readerThread = null;
                SimpleLogger.debug("Reader thread stopped");
            }

            if (networkExecutor != null && !networkExecutor.isShutdown()) {
                networkExecutor.shutdownNow();
                SimpleLogger.debug("Network executor shutdown");
            }

            if (out != null) {
                out.close();
                SimpleLogger.debug("Output stream closed");
            }
            if (in != null) {
                in.close();
                SimpleLogger.debug("Input stream closed");
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                SimpleLogger.debug("Socket closed");
            }

            connected = false;
            statusLabel.setText("未连接");
            historyPanel.removeAll();
            historyPanel.revalidate();
            historyPanel.repaint();
            historyItems.clear();
            updateComponentStates();

            SimpleLogger.connectionStatus("DISCONNECTED", "Successfully disconnected from server");
        } catch (IOException e) {
            SimpleLogger.error("Error occurred while disconnecting from server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "断开连接时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendText() {
        if (!connected) {
            SimpleLogger.warn("Attempted to send text while not connected");
            JOptionPane.showMessageDialog(this, "请先连接到服务器！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            SimpleLogger.warn("Attempted to send empty text");
            JOptionPane.showMessageDialog(this, "请输入要发送的文本！", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SimpleLogger.guiAction("SEND_TEXT", "User " + username + " initiated sending of text (length: " + text.length() + " chars)");

        networkExecutor.submit(() -> {
            try {
                String historyJson = fetchHistoryJson();
                if (textExistsInHistory(text, historyJson)) {
                    SimpleLogger.warn("Attempted to send duplicate text that already exists in history: " + text);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "该内容已存在于共享剪贴板历史中！\n请修改内容后再发送。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    });
                    return;
                }

                String pushData = username + "\n" + text;
                SimpleLogger.networkOperation("SEND_PUSH_REQUEST", "Sending PUSH message with text: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));

                byte[] pushMsg = Protocol.createPushMessage(pushData);
                out.write(pushMsg);
                out.flush();
                SimpleLogger.dataTransfer("OUTGOING", "PUSH_REQUEST", pushMsg.length, "Text pushed to server");

                Protocol.Message response = pollResponse();
                if (response == null) {
                    throw new IOException("Interrupted while waiting for push response");
                }

                SwingUtilities.invokeLater(() -> {
                    if (response.isSuccessful()) {
                        SimpleLogger.info("Text sent successfully to server");
                        inputArea.setText("");
                        refreshHistory();
                    } else {
                        SimpleLogger.error("Failed to send text: " + response.getData());
                        JOptionPane.showMessageDialog(this, "发送失败: " + response.getData(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (IOException e) {
                SimpleLogger.error("IO Exception occurred while sending text", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "发送文本时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    connected = false;
                    updateComponentStates();
                });
            }
        });
    }

    private String fetchHistoryJson() throws IOException {
        byte[] historyMsg = Protocol.createHistoryMessage();
        out.write(historyMsg);
        out.flush();

        while (true) {
            Protocol.Message msg = pollResponse();
            if (msg == null) {
                throw new IOException("Interrupted while waiting for response");
            }
            if (msg.getCmd() == Protocol.CMD_OK || msg.getCmd() == Protocol.CMD_ERROR) {
                return msg.getData();
            }
        }
    }

    private boolean textExistsInHistory(String text, String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return false;
        }
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return json.contains("\"text\":\"" + escaped + "\"");
    }

    private void refreshHistory() {
        if (!connected) {
            SimpleLogger.warn("Attempted to refresh history while not connected");
            return;
        }

        SimpleLogger.debug("Initiating history refresh");

        networkExecutor.submit(() -> {
            try {
                String historyJson = fetchHistoryJson();
                SwingUtilities.invokeLater(() -> {
                    SimpleLogger.info("Successfully retrieved history from server");
                    parseAndDisplayHistory(historyJson);
                });
            } catch (IOException e) {
                SimpleLogger.error("IO Exception occurred while refreshing history", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "获取历史记录时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    connected = false;
                    updateComponentStates();
                });
            }
        });
    }

    private void deleteHistoryItem(int index) {
        if (!connected) return;

        SimpleLogger.guiAction("DELETE_HISTORY", "User " + username + " requesting to delete history item at index " + index);

        networkExecutor.submit(() -> {
            try {
                byte[] deleteMsg = Protocol.createDeleteMessage(index);
                out.write(deleteMsg);
                out.flush();
                SimpleLogger.dataTransfer("OUTGOING", "DELETE_REQUEST", deleteMsg.length, "Deleting history item at index " + index);

                Protocol.Message response = pollResponse();
                if (response == null) {
                    throw new IOException("Interrupted while waiting for delete response");
                }

                SwingUtilities.invokeLater(() -> {
                    if (response.isSuccessful()) {
                        SimpleLogger.info("Successfully deleted history item at index " + index);
                        refreshHistory();
                    } else {
                        SimpleLogger.error("Failed to delete history item: " + response.getData());
                        JOptionPane.showMessageDialog(this, "删除失败: " + response.getData(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (IOException e) {
                SimpleLogger.error("IO Exception occurred while deleting history item", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "删除时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    connected = false;
                    updateComponentStates();
                });
            }
        });
    }

    private void parseAndDisplayHistory(String json) {
        List<HistoryItem> items = new ArrayList<>();

        if (json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1).trim();

            if (!content.isEmpty()) {
                int braceDepth = 0;
                int quoteLevel = 0;
                int objStart = -1;

                for (int i = 0; i < content.length(); i++) {
                    char c = content.charAt(i);
                    if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                        quoteLevel = 1 - quoteLevel;
                    } else if (quoteLevel == 0) {
                        if (c == '{') {
                            if (braceDepth == 0) objStart = i;
                            braceDepth++;
                        } else if (c == '}') {
                            braceDepth--;
                            if (braceDepth == 0 && objStart >= 0) {
                                String obj = content.substring(objStart, i + 1);
                                HistoryItem item = parseJsonObject(obj);
                                if (item != null) {
                                    items.add(item);
                                }
                                objStart = -1;
                            }
                        }
                    }
                }
            }
        }

        historyItems.clear();
        historyItems.addAll(items);

        historyPanel.removeAll();

        addHeaderRow(0);

        int row = 1;
        for (int i = 0; i < historyItems.size(); i++) {
            HistoryItem item = historyItems.get(i);
            addHistoryRow(item, i, row);
            row++;
            addSeparatorRow(row);
            row++;
        }

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        historyPanel.add(Box.createVerticalGlue(), filler);

        historyPanel.revalidate();
        historyPanel.repaint();
    }

    private void addHeaderRow(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 4);

        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel userHeader = new JLabel("用户名");
        userHeader.setFont(userHeader.getFont().deriveFont(Font.BOLD, 12f));
        userHeader.setForeground(new Color(0xAA, 0xAA, 0xCC));
        userHeader.setPreferredSize(new Dimension(100, 22));
        historyPanel.add(userHeader, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel contentHeader = new JLabel("内容");
        contentHeader.setFont(contentHeader.getFont().deriveFont(Font.BOLD, 12f));
        contentHeader.setForeground(new Color(0xAA, 0xAA, 0xCC));
        historyPanel.add(contentHeader, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JLabel actionHeader = new JLabel("操作");
        actionHeader.setFont(actionHeader.getFont().deriveFont(Font.BOLD, 12f));
        actionHeader.setForeground(new Color(0xAA, 0xAA, 0xCC));
        actionHeader.setPreferredSize(new Dimension(110, 22));
        historyPanel.add(actionHeader, gbc);
    }

    private void addSeparatorRow(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        historyPanel.add(new JSeparator(JSeparator.HORIZONTAL), gbc);
    }

    private void addHistoryRow(HistoryItem item, int index, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.insets = new Insets(4, 8, 4, 4);
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;

        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel userLabel = new JLabel(item.user);
        userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD, 12f));
        if (item.isOwn(username)) {
            userLabel.setForeground(new Color(0x60, 0xA5, 0xFA));
        } else {
            userLabel.setForeground(new Color(0xE0, 0xE0, 0xE0));
        }
        userLabel.setPreferredSize(new Dimension(100, 22));
        historyPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel textLabel = new JLabel("<html>" + escapeHtml(item.text) + "</html>");
        textLabel.setFont(textLabel.getFont().deriveFont(11f));
        historyPanel.add(textLabel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttonPanel.setOpaque(false);
        JButton copyButton = new DarkButton("复制", false);
        copyButton.setFont(copyButton.getFont().deriveFont(11f));
        copyButton.setMargin(new Insets(2, 8, 2, 8));
        copyButton.addActionListener(e -> copyToClipboard(item.text));
        buttonPanel.add(copyButton);

        if (item.isOwn(username)) {
            JButton deleteButton = new DarkButton("删除", false);
            deleteButton.setFont(deleteButton.getFont().deriveFont(11f));
            deleteButton.setMargin(new Insets(2, 8, 2, 8));
            deleteButton.setForeground(new Color(0xFF, 0x6B, 0x6B));
            final int idx = index;
            deleteButton.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "确定要删除这条记录吗？\n\"" + (item.text.length() > 40 ? item.text.substring(0, 40) + "..." : item.text) + "\"",
                        "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    deleteHistoryItem(idx);
                }
            });
            buttonPanel.add(deleteButton);
        }
        buttonPanel.setPreferredSize(new Dimension(110, 28));
        historyPanel.add(buttonPanel, gbc);
    }

    private HistoryItem parseJsonObject(String obj) {
        String user = "";
        String text = "";
        boolean inKey = false;
        boolean inValue = false;
        String currentKey = "";
        StringBuilder currentValue = new StringBuilder();
        int quoteLevel = 0;

        for (int i = 0; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '"' && (i == 0 || obj.charAt(i - 1) != '\\')) {
                quoteLevel = 1 - quoteLevel;
                if (quoteLevel == 1) {
                    if (!inKey && !inValue) {
                        inKey = true;
                    }
                } else {
                    if (inKey) {
                        inKey = false;
                    } else if (inValue) {
                        inValue = false;
                        String value = currentValue.toString();
                        if ("user".equals(currentKey)) {
                            user = value;
                        } else if ("text".equals(currentKey)) {
                            text = value;
                        }
                        currentValue.setLength(0);
                        currentKey = "";
                    }
                }
                continue;
            }
            if (quoteLevel == 1) {
                if (inKey) {
                    currentKey += c;
                } else if (inValue) {
                    currentValue.append(c);
                }
            } else if (c == ':' && quoteLevel == 0) {
                if (!currentKey.isEmpty()) {
                    inValue = true;
                }
            }
        }

        if (!user.isEmpty() || !text.isEmpty()) {
            return new HistoryItem(user, text, -1);
        }
        return null;
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
        SimpleLogger.guiAction("COPY_TO_CLIPBOARD", "Copied text to system clipboard: " +
                (text.length() > 50 ? text.substring(0, 50) + "..." : text));
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private void startReaderThread() {
        responseQueue = new LinkedBlockingQueue<>();
        running = true;
        readerThread = new Thread(() -> {
            SimpleLogger.info("Reader thread started");
            while (running && connected) {
                try {
                    byte[] header = new byte[5];
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
                    Protocol.Message msg = Protocol.unpack(fullMsg);

                    if (msg.getCmd() == Protocol.CMD_NOTIFY_REFRESH) {
                        SimpleLogger.info("Received NOTIFY_REFRESH from server");
                        SwingUtilities.invokeLater(() -> {
                            if (connected) {
                                refreshHistory();
                            }
                        });
                    } else {
                        responseQueue.offer(msg);
                    }
                } catch (IOException e) {
                    if (running) {
                        SimpleLogger.error("Reader thread IO error: " + e.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            connected = false;
                            updateComponentStates();
                        });
                    }
                    break;
                }
            }
            SimpleLogger.info("Reader thread stopped");
        }, "ServerReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private Protocol.Message pollResponse() {
        try {
            return responseQueue.poll(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void updateComponentStates() {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        hostField.setEditable(!connected);
        portField.setEditable(!connected);
        usernameField.setEditable(!connected);
        sendButton.setEnabled(connected);
        refreshButton.setEnabled(connected);

        if (connected) {
            statusLabel.setForeground(new Color(0x4A, 0xDE, 0x80));
        } else {
            statusLabel.setForeground(new Color(0xFF, 0x6B, 0x6B));
        }
    }

    private static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, width - 1, height - 1, radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2 + 1, radius / 2 + 1, radius / 2 + 1, radius / 2 + 1);
        }
    }

    private static class DarkButton extends JButton {
        private final Color defaultBg = new Color(0x1E, 0x1E, 0x2E);
        private final Color hoverBg = new Color(0x2A, 0x2A, 0x40);
        private final Color accentBg = new Color(0x3B, 0x82, 0xF6);
        private final Color accentHover = new Color(0x60, 0xA5, 0xFA);
        private final boolean isAccent;

        DarkButton(String text, boolean accent) {
            super(text);
            this.isAccent = accent;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.PLAIN, 12f));
            setMargin(new Insets(6, 14, 6, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { setCursor(new Cursor(Cursor.HAND_CURSOR)); repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color bg = defaultBg;
            if (isAccent) {
                bg = getModel().isRollover() ? accentHover : accentBg;
            } else {
                bg = getModel().isRollover() ? hoverBg : defaultBg;
            }
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 8, 8));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class DarkTextField extends JTextField {
        DarkTextField(String text, int cols) {
            super(text, cols);
            setBackground(new Color(0x1E, 0x1E, 0x2E));
            setForeground(new Color(0xE0, 0xE0, 0xE0));
            setCaretColor(new Color(0xE0, 0xE0, 0xE0));
            setBorder(new RoundedBorder(8, new Color(0x33, 0x33, 0x55)));
        }
    }

    public static void main(String[] args) {
        SimpleLogger.init("clipboard_client_" + System.currentTimeMillis() + ".log");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                applyDarkTheme();
            } catch (Exception e) {
                e.printStackTrace();
            }
            new GUIClipboardClient().setVisible(true);
        });
    }

    private static void applyDarkTheme() {
        Color bg = new Color(0x0F, 0x0F, 0x1A);
        Color panelBg = new Color(0x1A, 0x1A, 0x2E);
        Color fg = new Color(0xE0, 0xE0, 0xE0);
        Color accent = new Color(0x3B, 0x82, 0xF6);

        UIManager.put("Panel.background", panelBg);
        UIManager.put("OptionPane.background", panelBg);
        UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("TitledBorder.titleColor", new Color(0xAA, 0xAA, 0xCC));
        UIManager.put("TextField.background", new Color(0x1E, 0x1E, 0x2E));
        UIManager.put("TextField.foreground", fg);
        UIManager.put("TextField.caretForeground", fg);
        UIManager.put("TextArea.background", new Color(0x1E, 0x1E, 0x2E));
        UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextArea.caretForeground", fg);
        UIManager.put("ScrollPane.background", panelBg);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.background", panelBg);
        UIManager.put("SplitPane.dividerSize", 2);
        UIManager.put("Separator.foreground", new Color(0x33, 0x33, 0x55));
        UIManager.put("ToolTip.background", new Color(0x2A, 0x2A, 0x40));
        UIManager.put("ToolTip.foreground", fg);
    }
}