package com.clipboard.client;

import com.clipboard.protocol.Protocol;

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
        if (connected) return;
        
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "无效的端口号！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
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
            
            JOptionPane.showMessageDialog(this, "成功连接到服务器！", "连接成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "连接失败: " + e.getMessage(), "连接错误", JOptionPane.ERROR_MESSAGE);
            connected = false;
            updateComponentStates();
        }
    }

    private void disconnectFromServer() {
        if (!connected) return;
        
        try {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
            
            connected = false;
            statusLabel.setText("未连接");
            updateComponentStates();
            
            JOptionPane.showMessageDialog(this, "已断开连接", "断开连接", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "断开连接时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendText() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "请先连接到服务器！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入要发送的文本！", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            byte[] pushMsg = Protocol.createPushMessage(text);
            out.write(pushMsg);
            out.flush();

            // 读取响应
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

            if (response.isSuccessful()) {
                JOptionPane.showMessageDialog(this, "文本发送成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                inputArea.setText(""); // 清空输入框
                
                // 刷新历史记录以显示新发送的文本
                refreshHistory();
            } else {
                JOptionPane.showMessageDialog(this, "发送失败: " + response.getData(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "发送文本时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            connected = false;
            updateComponentStates();
        }
    }

    private void refreshHistory() {
        if (!connected) return;
        
        try {
            // 发送历史请求
            byte[] historyMsg = Protocol.createHistoryMessage();
            out.write(historyMsg);
            out.flush();

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

            if (response.isSuccessful()) {
                // 解析JSON历史记录
                parseAndDisplayHistory(historyJson);
            } else {
                JOptionPane.showMessageDialog(this, "获取历史记录失败: " + response.getData(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "获取历史记录时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            connected = false;
            updateComponentStates();
        }
    }

    private void parseAndDisplayHistory(String json) {
        // 简单解析JSON数组（这里为了不引入外部库，手动解析）
        List<String> historyItems = new ArrayList<>();
        
        if (json.startsWith("[") && json.endsWith("]")) {
            String content = json.substring(1, json.length() - 1); // 去掉首尾的 []
            
            if (!content.isEmpty()) {
                // 按逗号分割，注意跳过引号内的逗号
                int bracketLevel = 0;
                StringBuilder item = new StringBuilder();
                
                for (int i = 0; i < content.length(); i++) {
                    char c = content.charAt(i);
                    
                    if (c == '[') {
                        bracketLevel++;
                        item.append(c);
                    } else if (c == ']') {
                        bracketLevel--;
                        item.append(c);
                    } else if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                        // 处理引号，但跳过转义的引号
                        item.append(c);
                    } else if (c == ',' && bracketLevel == 0) {
                        // 在外层遇到逗号，说明是一个完整的项目
                        historyItems.add(item.toString().trim().replaceAll("^\"|\"$", "")); // 去掉首尾引号
                        item = new StringBuilder();
                    } else {
                        item.append(c);
                    }
                }
                
                // 添加最后一个项目
                if (item.length() > 0) {
                    historyItems.add(item.toString().trim().replaceAll("^\"|\"$", ""));
                }
            }
        }
        
        // 更新列表模型
        listModel.clear();
        for (String item : historyItems) {
            listModel.addElement(item);
        }
    }

    private void copySelectedText() {
        String selectedValue = historyList.getSelectedValue();
        if (selectedValue != null) {
            // 复制到系统剪贴板
            StringSelection selection = new StringSelection(selectedValue);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            
            JOptionPane.showMessageDialog(this, "已复制到系统剪贴板:\n" + selectedValue, "复制成功", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void startAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        
        refreshTimer = new Timer(5000, e -> refreshHistory()); // 每5秒刷新一次
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