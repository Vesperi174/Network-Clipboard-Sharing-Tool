package com.clipboard.util;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * 简单的日志系统，用于记录GUI界面和后台的操作。
 * 每个进程使用独立的日志文件，避免 Windows 文件锁冲突。
 *
 * 使用方式：
 *   SimpleLogger.init("my_app.log");  // 先初始化
 *   SimpleLogger.info("xxx");          // 再记录日志
 */
public class SimpleLogger {
    private static Logger logger;
    private static boolean initialized = false;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 初始化日志系统，必须在任何日志记录之前调用。
     *
     * @param logFileName 日志文件名（如 "clipboard_server.log"）
     */
    public static void init(String logFileName) {
        if (initialized) return;

        try {
            // 确保 logs 目录存在
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            logger = Logger.getLogger(SimpleLogger.class.getName());
            logger.setLevel(Level.ALL);

            SimpleFormatter formatter = new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format("[%s] [%s] %s%n",
                            LocalDateTime.now().format(DTF),
                            lr.getLevel(),
                            lr.getMessage());
                }
            };

            File logFile = new File(logsDir, logFileName);
            FileHandler fileHandler = new FileHandler(logFile.getPath(), true);
            fileHandler.setFormatter(formatter);
            fileHandler.setLevel(Level.ALL);

            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(false);

            initialized = true;
            logger.info("Logger initialized, writing to: " + logFile.getPath());

        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init("clipboard_app.log");
        }
    }

    public static void info(String message) {
        ensureInitialized();
        logger.info(message);
    }

    public static void warn(String message) {
        ensureInitialized();
        logger.warning(message);
    }

    public static void error(String message) {
        ensureInitialized();
        logger.severe(message);
    }

    public static void error(String message, Throwable t) {
        ensureInitialized();
        logger.log(Level.SEVERE, message, t);
    }

    public static void debug(String message) {
        ensureInitialized();
        logger.fine(message);
    }

    public static void guiAction(String action, String details) {
        ensureInitialized();
        logger.info(String.format("GUI Action - %s: %s", action, details));
    }

    public static void networkOperation(String operation, String details) {
        ensureInitialized();
        logger.info(String.format("Network Operation - %s: %s", operation, details));
    }

    public static void dataTransfer(String direction, String dataType, int dataSize, String details) {
        ensureInitialized();
        logger.info(String.format("Data Transfer - %s %s (%d bytes): %s",
                direction, dataType, dataSize, details));
    }

    public static void connectionStatus(String status, String details) {
        ensureInitialized();
        logger.info(String.format("Connection Status - %s: %s", status, details));
    }

    public static void applicationEvent(String event, String details) {
        ensureInitialized();
        logger.info(String.format("Application Event - %s: %s", event, details));
    }
}