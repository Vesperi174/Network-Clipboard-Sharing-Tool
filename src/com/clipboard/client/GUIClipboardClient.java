package com.clipboard.client;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private String lastSentText = "";
    private Timer refreshTimer;
    private ExecutorService networkExecutor;
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
        hostField = new JTextField("127.0.0.1", 12);
        portField = new JTextField("8888", 6);
        usernameField = new JTextField("", 10);
        connectButton = new JButton("连接");
        disconnectButton = new JButton("断开");
        statusLabel = new JLabel("未连接");

        historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyScrollPane = new JScrollPane(historyPanel);
        historyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        historyScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputScrollPane = new JScrollPane(inputArea);
        sendButton = new JButton("发送到共享剪贴板");
        refreshButton = new JButton("刷新历史");
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectPanel.setBorder(BorderFactory.createTitledBorder("连接设置"));
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
        historyOuterPanel.setBorder(BorderFactory.createTitledBorder("共享剪贴板历史"));
        historyOuterPanel.add(historyScrollPane, BorderLayout.CENTER);

        JPanel historyButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        historyButtonPanel.add(refreshButton);
        historyOuterPanel.add(historyButtonPanel, BorderLayout.SOUTH);

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.setBorder(BorderFactory.createTitledBorder("发送文本"));
        sendPanel.add(inputScrollPane, BorderLayout.CENTER);
        sendPanel.add(sendButton, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyOuterPanel, sendPanel);
        splitPane.setDividerLocation(400);

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
            startAutoRefresh();
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

            if (refreshTimer != null) {
                refreshTimer.stop();
                SimpleLogger.debug("Auto-refresh timer stopped");
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

        if (text.equals(lastSentText)) {
            SimpleLogger.warn("Attempted to send duplicate text: " + text);
            JOptionPane.showMessageDialog(this, "该内容与上次发送相同，请修改后再发送！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        lastSentText = text;
        SimpleLogger.guiAction("SEND_TEXT", "User " + username + " initiated sending of text (length: " + text.length() + " chars)");

        networkExecutor.submit(() -> {
            try {
                String pushData = username + "\n" + text;
                SimpleLogger.networkOperation("SEND_PUSH_REQUEST", "Sending PUSH message with text: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));

                byte[] pushMsg = Protocol.createPushMessage(pushData);
                out.write(pushMsg);
                out.flush();
                SimpleLogger.dataTransfer("OUTGOING", "PUSH_REQUEST", pushMsg.length, "Text pushed to server");

                byte[] responseHeader = new byte[5];
                in.readFully(responseHeader);
                Protocol.Message response = Protocol.unpack(responseHeader);

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

    private void refreshHistory() {
        if (!connected) {
            SimpleLogger.warn("Attempted to refresh history while not connected");
            return;
        }

        SimpleLogger.debug("Initiating history refresh");

        networkExecutor.submit(() -> {
            try {
                byte[] historyMsg = Protocol.createHistoryMessage();
                SimpleLogger.networkOperation("SEND_HISTORY_REQUEST", "Sending HISTORY request to server");

                out.write(historyMsg);
                out.flush();
                SimpleLogger.dataTransfer("OUTGOING", "HISTORY_REQUEST", historyMsg.length, "Requesting history from server");

                byte[] header = new byte[5];
                in.readFully(header);
                int dataLength = ((header[1] & 0xFF) << 24)
                        | ((header[2] & 0xFF) << 16)
                        | ((header[3] & 0xFF) << 8)
                        | (header[4] & 0xFF);

                byte[] fullMessage = new byte[5 + dataLength];
                System.arraycopy(header, 0, fullMessage, 0, 5);
                if (dataLength > 0) {
                    in.readFully(fullMessage, 5, dataLength);
                }
                Protocol.Message response = Protocol.unpack(fullMessage);
                String historyJson = response.getData();

                SwingUtilities.invokeLater(() -> {
                    if (response.isSuccessful()) {
                        SimpleLogger.info("Successfully retrieved history from server");
                        parseAndDisplayHistory(historyJson);
                    } else {
                        SimpleLogger.error("Failed to retrieve history: " + response.getData());
                        JOptionPane.showMessageDialog(this, "获取历史记录失败: " + response.getData(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
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

                byte[] responseHeader = new byte[5];
                in.readFully(responseHeader);
                Protocol.Message response = Protocol.unpack(responseHeader);

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
        for (int i = 0; i < historyItems.size(); i++) {
            HistoryItem item = historyItems.get(i);
            historyPanel.add(createHistoryItemPanel(item, i));
        }
        historyPanel.revalidate();
        historyPanel.repaint();
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

    private JPanel createHistoryItemPanel(HistoryItem item, int index) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        JPanel leftPanel = new JPanel(new BorderLayout(3, 2));
        JLabel userLabel = new JLabel(item.user);
        userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD, 12f));
        if (item.isOwn(username)) {
            userLabel.setForeground(new Color(0, 120, 212));
        }
        leftPanel.add(userLabel, BorderLayout.NORTH);

        JLabel textLabel = new JLabel("<html><body style='width:400px'>" + escapeHtml(item.text) + "</body></html>");
        textLabel.setFont(textLabel.getFont().deriveFont(11f));
        leftPanel.add(textLabel, BorderLayout.CENTER);

        panel.add(leftPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        JButton copyButton = new JButton("复制");
        copyButton.setFont(copyButton.getFont().deriveFont(11f));
        copyButton.setMargin(new Insets(2, 8, 2, 8));
        copyButton.addActionListener(e -> copyToClipboard(item.text));
        buttonPanel.add(copyButton);

        if (item.isOwn(username)) {
            JButton deleteButton = new JButton("删除");
            deleteButton.setFont(deleteButton.getFont().deriveFont(11f));
            deleteButton.setMargin(new Insets(2, 8, 2, 8));
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

        panel.add(buttonPanel, BorderLayout.EAST);
        return panel;
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

    private void startAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        refreshTimer = new Timer(30000, e -> refreshHistory());
        refreshTimer.start();
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
            statusLabel.setForeground(Color.GREEN.darker());
        } else {
            statusLabel.setForeground(Color.RED);
        }
    }

    public static void main(String[] args) {
        SimpleLogger.init("clipboard_client_" + System.currentTimeMillis() + ".log");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                try {
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            new GUIClipboardClient().setVisible(true);
        });
    }
}