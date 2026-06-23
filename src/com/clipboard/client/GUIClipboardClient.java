package com.clipboard.client;

import com.clipboard.protocol.Protocol;
import com.clipboard.util.SimpleLogger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络剪贴板共享工具 - GUI客户端
 * <p>
 * 带有图形界面的客户端，显示所有用户的历史记录，支持可视化选择复制。
 */
public class GUIClipboardClient extends JFrame {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    
    private JTextField hostField;
    private JTextField portField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JList<String> historyList;
    private DefaultListModel<String> listModel;
    private JButton refreshButton;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;
    
    private boolean connected = false;
    private boolean sending = false;
    private String lastSentText = "";
    private Timer refreshTimer;
    
    private JScrollPane scrollPane;
    private JScrollPane inputScrollPane;

    public GUIClipboardClient() {
        setTitle("网络剪贴板共享工具 - GUI客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        
        // 初始化时禁用相关组件
        updateComponentStates();
    }

    private void initializeComponents() {
        // 连接面板组件
        hostField = new JTextField("127.0.0.1", 15);
        portField = new JTextField("8888", 10);
        connectButton = new JButton("连接");
        disconnectButton = new JButton("断开");
        statusLabel = new JLabel("未连接");
        
        // 历史记录面板组件
        listModel = new DefaultListModel<>();
        historyList = new JList<>(listModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scrollPane = new JScrollPane(historyList);
        
        // 发送面板组件
        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputScrollPane = new JScrollPane(inputArea);
        sendButton = new JButton("发送到共享剪贴板");
        refreshButton = new JButton("刷新历史");
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // 连接面板
        JPanel connectPanel = new JPanel(new FlowLayout());
        connectPanel.setBorder(BorderFactory.createTitledBorder("连接设置"));
        connectPanel.add(new JLabel("主机: "));
        connectPanel.add(hostField);
        connectPanel.add(new JLabel("端口: "));
        connectPanel.add(portField);
        connectPanel.add(connectButton);
        connectPanel.add(disconnectButton);
        connectPanel.add(statusLabel);
        
        // 历史记录面板
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBorder(BorderFactory.createTitledBorder("共享剪贴板历史"));
        historyPanel.add(scrollPane, BorderLayout.CENTER);
        historyPanel.add(refreshButton, BorderLayout.SOUTH);
        
        // 发送面板
        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.setBorder(BorderFactory.createTitledBorder("发送文本"));
        sendPanel.add(inputScrollPane, BorderLayout.CENTER);
        sendPanel.add(sendButton, BorderLayout.SOUTH);
        
        // 主面板布局
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyPanel, sendPanel);
        splitPane.setDividerLocation(400);
        
        add(connectPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        sendButton.addActionListener(e -> sendText());
        refreshButton.addActionListener(e -> refreshHistory());
        
        // 双击历史记录项复制到系统剪贴板
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) { // 双击
                    copySelectedText();
                }
            }
        });
        
        // 回车键发送文本
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "sendText");
        inputArea.getActionMap().put("sendText", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!inputArea.getText().isEmpty()) {
                    sendText();
                }
            }
        });
        
        // 禁用Ctrl+Enter的换行功能，允许在文本区域中换行
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "sendText");
    }

    private void connectToServer() {
        if (connected) {
            SimpleLogger.warn("Attempted to connect while already connected");
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
            SimpleLogger.info("Attempting to connect to server " + host + ":" + port);
            socket = new Socket(host, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            connected = true;
            
            statusLabel.setText("已连接到 " + host + ":" + port);
            updateComponentStates();
            
            // 启动定时刷新历史记录
            startAutoRefresh();
            
            // 初始加载历史记录
            refreshHistory();
            
            SimpleLogger.connectionStatus("CONNECTED", "Successfully connected to " + host + ":" + port);
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
            updateComponentStates();
            
            SimpleLogger.connectionStatus("DISCONNECTED", "Successfully disconnected from server");
            JOptionPane.showMessageDialog(this, "已断开连接", "断开连接", JOptionPane.INFORMATION_MESSAGE);
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

        if (sending) {
            SimpleLogger.warn("Send in progress, ignoring duplicate click");
            JOptionPane.showMessageDialog(this, "正在发送中，请稍候...", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        sending = true;
        lastSentText = text;
        SimpleLogger.guiAction("SEND_TEXT", "User initiated sending of text (length: " + text.length() + " chars)");
        
        Thread sendThread = new Thread(() -> {
            try {
                SimpleLogger.networkOperation("SEND_PUSH_REQUEST", "Sending PUSH message with text: " + (text.length() > 50 ? text.substring(0, 50) + "..." : text));
                
                byte[] pushMsg = Protocol.createPushMessage(text);
                out.write(pushMsg);
                out.flush();
                
                SimpleLogger.dataTransfer("OUTGOING", "PUSH_REQUEST", pushMsg.length, "Text pushed to server");

                byte[] responseHeader = new byte[5];
                in.readFully(responseHeader);
                Protocol.Message response = Protocol.unpack(responseHeader);

                GUIClipboardClient self = this;
                Protocol.Message finalResponse = response;
                SwingUtilities.invokeLater(() -> {
                    if (finalResponse.isSuccessful()) {
                        SimpleLogger.info("Text sent successfully to server");
                        JOptionPane.showMessageDialog(self, "文本发送成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                        inputArea.setText("");
                        refreshHistory();
                    } else {
                        SimpleLogger.error("Failed to send text: " + finalResponse.getData());
                        JOptionPane.showMessageDialog(self, "发送失败: " + finalResponse.getData(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                    sending = false;
                });
            } catch (IOException e) {
                SimpleLogger.error("IO Exception occurred while sending text", e);
                GUIClipboardClient self = this;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(self, "发送文本时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    self.connected = false;
                    self.updateComponentStates();
                    sending = false;
                });
            }
        });
        
        sendThread.start();
    }

    private void refreshHistory() {
        if (!connected) {
            SimpleLogger.warn("Attempted to refresh history while not connected");
            return;
        }
        
        SimpleLogger.debug("Initiating history refresh");
        
        // 在后台线程中执行网络操作，避免阻塞UI线程
        Thread refreshThread = new Thread(() -> {
            try {
                // 发送历史请求
                byte[] historyMsg = Protocol.createHistoryMessage();
                SimpleLogger.networkOperation("SEND_HISTORY_REQUEST", "Sending HISTORY request to server");
                out.write(historyMsg);
                out.flush();
                
                SimpleLogger.dataTransfer("OUTGOING", "HISTORY_REQUEST", historyMsg.length, "Requesting history from server");

                // 读取响应
                byte[] responseHeader = new byte[5];
                in.readFully(responseHeader);
                Protocol.Message response = Protocol.unpack(responseHeader);

                int dataLength = ((responseHeader[1] & 0xFF) << 24)
                        | ((responseHeader[2] & 0xFF) << 16)
                        | ((responseHeader[3] & 0xFF) << 8)
                        | (responseHeader[4] & 0xFF);
                String historyJson = "";
                if (dataLength > 0) {
                    byte[] dataBytes = new byte[dataLength];
                    in.readFully(dataBytes);
                    historyJson = new String(dataBytes, "UTF-8");
                }

                // 在EDT中更新UI
                GUIClipboardClient self = this; // 保存this引用以在lambda中使用
                String jsonCopy = historyJson; // 保存historyJson引用以在lambda中使用
                Protocol.Message finalResponse = response; // 保存response引用以在lambda中使用
                SwingUtilities.invokeLater(() -> {
                    if (finalResponse.isSuccessful()) {
                        SimpleLogger.info("Successfully retrieved history from server, items count: " + 
                            (jsonCopy.isEmpty() ? 0 : jsonCopy.split(",\"").length));
                        // 解析JSON历史记录
                        self.parseAndDisplayHistory(jsonCopy);
                    } else {
                        SimpleLogger.error("Failed to retrieve history: " + finalResponse.getData());
                        JOptionPane.showMessageDialog(self, "获取历史记录失败: " + finalResponse.getData(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (IOException e) {
                SimpleLogger.error("IO Exception occurred while refreshing history", e);
                // 在EDT中更新UI
                GUIClipboardClient self = this; // 保存this引用以在lambda中使用
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(self, "获取历史记录时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    self.connected = false;
                    self.updateComponentStates();
                });
            }
        });
        
        refreshThread.start();
    }

    private void parseAndDisplayHistory(String json) {
        // 简单解析JSON数组（这里为了不引入外部库，手动解析）
        List<String> historyItems = new ArrayList<>();
        
        // 移除首尾的方括号
        if (json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1).trim();
            
            if (!content.isEmpty()) {
                // 按逗号分割，但要跳过引号内的逗号
                int quoteLevel = 0;  // 引号层级，用来区分是否在引号内
                int lastSplit = 0;   // 上一个分割点的位置
                
                for (int i = 0; i < content.length(); i++) {
                    char c = content.charAt(i);
                    
                    if (c == '"') {
                        // 检查是否是转义的引号
                        if (i == 0 || content.charAt(i - 1) != '\\') {
                            quoteLevel = 1 - quoteLevel; // 切换引号层级
                        }
                    } else if (c == ',' && quoteLevel == 0) { // 只有在引号外的逗号才分割
                        String element = content.substring(lastSplit, i).trim();
                        // 去掉首尾的引号并处理转义字符
                        element = unescapeJsonString(element);
                        historyItems.add(element);
                        lastSplit = i + 1; // 下一个元素从逗号后开始
                    }
                }
                
                // 添加最后一个元素
                String lastElement = content.substring(lastSplit).trim();
                if (!lastElement.isEmpty()) {
                    lastElement = unescapeJsonString(lastElement);
                    historyItems.add(lastElement);
                }
            }
        }
        
        // 更新列表模型
        listModel.clear();
        for (String item : historyItems) {
            listModel.addElement(item);
        }
    }
    
    /**
     * 反转义JSON字符串中的特殊字符
     * @param str 包含转义字符的字符串
     * @return 反转义后的字符串
     */
    private String unescapeJsonString(String str) {
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            str = str.substring(1, str.length() - 1); // 去掉首尾引号
        }
        
        // 处理常见的JSON转义字符
        str = str.replace("\\\"", "\"")
                 .replace("\\\\", "\\")
                 .replace("\\/", "/")
                 .replace("\\b", "\b")
                 .replace("\\f", "\f")
                 .replace("\\n", "\n")
                 .replace("\\r", "\r")
                 .replace("\\t", "\t");
        
        return str;
    }

    private void copySelectedText() {
        String selectedValue = historyList.getSelectedValue();
        if (selectedValue != null) {
            // 复制到系统剪贴板
            StringSelection selection = new StringSelection(selectedValue);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            
            SimpleLogger.guiAction("COPY_TO_CLIPBOARD", "Copied text to system clipboard: " + 
                (selectedValue.length() > 50 ? selectedValue.substring(0, 50) + "..." : selectedValue));
            
            JOptionPane.showMessageDialog(this, "已复制到系统剪贴板:\n" + selectedValue, "复制成功", JOptionPane.INFORMATION_MESSAGE);
        } else {
            SimpleLogger.warn("Attempted to copy null or unselected value from history list");
        }
    }

    private void startAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        
        refreshTimer = new Timer(30000, e -> refreshHistory()); // 每30秒刷新一次
        refreshTimer.start();
    }

    private void updateComponentStates() {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        hostField.setEditable(!connected);
        portField.setEditable(!connected);
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