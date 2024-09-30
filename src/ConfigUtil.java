package src;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigUtil {
    private static final String CONFIG_FILE = "config.json";

    public static JSONObject loadConfig() {
        try {
            if (!Files.exists(Paths.get(CONFIG_FILE))) {
                // Создаем дефолтные настройки
                JSONObject defaultConfig = new JSONObject();
                defaultConfig.put("taskName", "Задача 1");
                defaultConfig.put("workDuration", 25);
                defaultConfig.put("breakDuration", 5);
                saveConfig(defaultConfig);
                return defaultConfig;
            }

            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)));
            return new JSONObject(content);
        } catch (IOException e) {
            System.err.println("Не удалось загрузить конфигурацию: " + e.getMessage());
            // Возвращаем дефолтные настройки в случае ошибки
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("taskName", "Задача 1");
            defaultConfig.put("workDuration", 25);
            defaultConfig.put("breakDuration", 5);
            return defaultConfig;
        }
    }

    public static void saveConfig(JSONObject config) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            writer.write(config.toString(4)); // Красивое форматирование с отступами
        } catch (IOException e) {
            System.err.println("Не удалось сохранить конфигурацию: " + e.getMessage());
        }
    }
}