package src;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigUtil {
    private static final String CONFIG_FILE = "config.json";

    public static JSONObject loadConfig() {
        try {
            if (!Files.exists(Paths.get(CONFIG_FILE))) {
                // Создаем дефолтные настройки
                JSONObject defaultConfig = getDefaultConfig();
                saveConfig(defaultConfig);
                return defaultConfig;
            }

            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)));
            return new JSONObject(content);
        } catch (IOException e) {
            System.err.println("Не удалось загрузить конфигурацию: " + e.getMessage());
            // Возвращаем дефолтные настройки в случае ошибки
            JSONObject defaultConfig = getDefaultConfig();
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

    private static JSONObject getDefaultConfig() {
        JSONObject defaultConfig = new JSONObject();
        JSONArray tasksArray = new JSONArray();

        JSONObject task1 = new JSONObject();
        task1.put("name", "Задача 1");
        task1.put("workDuration", 25);
        task1.put("breakDuration", 5);

        JSONObject task2 = new JSONObject();
        task2.put("name", "Задача 2");
        task2.put("workDuration", 30);
        task2.put("breakDuration", 10);

        tasksArray.put(task1);
        tasksArray.put(task2);

        defaultConfig.put("tasks", tasksArray);
        defaultConfig.put("currentTaskIndex", 0);

        JSONObject pomodoroSettings = new JSONObject();
        pomodoroSettings.put("cyclesBeforeLongBreak", 4);
        pomodoroSettings.put("longBreakDuration", 15);
        defaultConfig.put("pomodoroSettings", pomodoroSettings);

        return defaultConfig;
    }

    // Методы для работы с задачами
    public static List<JSONObject> loadTasks() {
        JSONObject config = loadConfig();
        JSONArray tasksArray = config.getJSONArray("tasks");
        List<JSONObject> tasks = new ArrayList<>();
        for (int i = 0; i < tasksArray.length(); i++) {
            tasks.add(tasksArray.getJSONObject(i));
        }
        return tasks;
    }

    public static void saveTasks(List<JSONObject> tasks) {
        JSONObject config = loadConfig();
        config.put("tasks", new JSONArray(tasks));
        saveConfig(config);
    }

    // Методы для работы с Pomodoro настройками
    public static JSONObject loadPomodoroSettings() {
        JSONObject config = loadConfig();
        return config.getJSONObject("pomodoroSettings");
    }

    public static void savePomodoroSettings(JSONObject pomodoroSettings) {
        JSONObject config = loadConfig();
        config.put("pomodoroSettings", pomodoroSettings);
        saveConfig(config);
    }

    // Метод для получения текущей задачи
    public static JSONObject getCurrentTask() {
        JSONObject config = loadConfig();
        int currentTaskIndex = config.getInt("currentTaskIndex");
        JSONArray tasksArray = config.getJSONArray("tasks");
        if (currentTaskIndex >= 0 && currentTaskIndex < tasksArray.length()) {
            return tasksArray.getJSONObject(currentTaskIndex);
        } else {
            // Если индекс некорректен, возвращаем первую задачу
            return tasksArray.getJSONObject(0);
        }
    }

    // Метод для установки текущей задачи
    public static void setCurrentTaskIndex(int index) {
        JSONObject config = loadConfig();
        config.put("currentTaskIndex", index);
        saveConfig(config);
    }
}