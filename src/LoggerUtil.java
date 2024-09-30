package src;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LoggerUtil {
    private String logFilePath;

    public LoggerUtil() {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        logFilePath = "./logs/log_" + currentDate + ".jsonl"; // Изменено расширение на .jsonl
        createLogFileIfNotExists();
    }

    private void createLogFileIfNotExists() {
        File logDir = new File("./logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        try {
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Не удалось создать лог-файл: " + e.getMessage());
        }
    }

    public void log(String taskName, String action, int durationSeconds) {
        JSONObject logEntry = new JSONObject();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        logEntry.put("timestamp", timestamp);
        logEntry.put("task", taskName);
        logEntry.put("action", action);
        logEntry.put("duration_seconds", durationSeconds);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write(logEntry.toString() + "\n"); // Компактная запись JSON без отступов
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог-файл: " + e.getMessage());
        }
    }
}