package com.clipboard.server;

import com.clipboard.protocol.Protocol;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 剪贴板历史记录管理器
 * 负责管理共享剪贴板的历史记录
 */
public class ClipboardHistoryManager {
    private final List<String> history = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY_SIZE = 50;
    
    /**
     * 添加新的剪贴板内容到历史记录
     * @param content 新的剪贴板内容
     */
    public void addHistory(String content) {
        history.add(0, content); // 添加到前面
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1); // 删除最旧的记录
        }
    }
    
    /**
     * 获取历史记录
     * @return 历史记录列表
     */
    public List<String> getHistory() {
        return new CopyOnWriteArrayList<>(history); // 返回副本以保证线程安全
    }
    
    /**
     * 获取最新的剪贴板内容
     * @return 最新的剪贴板内容，如果没有则返回空字符串
     */
    public String getLatestContent() {
        return history.isEmpty() ? "" : history.get(0);
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