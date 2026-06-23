package com.clipboard.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 剪贴板历史记录管理器
 * 负责管理共享剪贴板的历史记录
 */
public class ClipboardHistoryManager {
    private final List<ClipboardHistoryEntry> history = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY_SIZE = 50;

    /**
     * 添加新的剪贴板内容到历史记录
     * @param user    发送者用户名
     * @param content 新的剪贴板内容
     */
    public void addHistory(String user, String content) {
        history.add(0, new ClipboardHistoryEntry(user, content));
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * 获取历史记录（直接返回内部列表，CopyOnWriteArrayList 迭代器自带快照隔离）
     * @return 历史记录列表
     */
    public List<ClipboardHistoryEntry> getHistory() {
        return history;
    }

    /**
     * 删除指定索引的历史记录
     * @param index 索引
     * @return 是否删除成功
     */
    public boolean deleteEntry(int index) {
        if (index >= 0 && index < history.size()) {
            history.remove(index);
            return true;
        }
        return false;
    }

    /**
     * 获取最新的剪贴板内容
     * @return 最新的剪贴板内容，如果没有则返回空字符串
     */
    public String getLatestContent() {
        return history.isEmpty() ? "" : history.get(0).getText();
    }

    /**
     * 清空历史记录
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * 获取历史记录大小
     * @return 历史记录条数
     */
    public int size() {
        return history.size();
    }
}