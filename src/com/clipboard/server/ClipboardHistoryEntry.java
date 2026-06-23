package com.clipboard.server;

/**
 * 剪贴板历史记录条目
 * 包含用户名、文本内容和时间戳
 */
public class ClipboardHistoryEntry {
    private final String user;
    private final String text;
    private final long timestamp;

    public ClipboardHistoryEntry(String user, String text) {
        this.user = user;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUser() {
        return user;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return user + ": " + text;
    }
}