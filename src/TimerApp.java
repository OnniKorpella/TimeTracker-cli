package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.file.Files;

public class TimerApp {
    private static boolean DEBUG_MODE = true; // Включить для отладки
    private static TrayIcon trayIcon;
    private static PopupMenu trayMenu;
    private static MenuItem trayTimerMenuItem;
    private static LoggerUtil logger;
    private static SoundUtil soundUtil;

    private static String currentTaskName = "Задача 1";
    private static int workDuration = 25; // минуты
    private static int breakDuration = 5; // минуты

    private static boolean isWorkPeriod = true;
    private static boolean isPaused = false;

    private static Timer swingTimer;
    private static int elapsedSeconds = 0;
    private static int remainingSeconds = workDuration * 60;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            debugLog("Запуск приложения...");
            // Загрузка конфигурации
            JSONObject config = ConfigUtil.loadConfig();
            currentTaskName = config.getString("taskName");
            workDuration = config.getInt("workDuration");
            breakDuration = config.getInt("breakDuration");
            remainingSeconds = isWorkPeriod ? workDuration * 60 : breakDuration * 60;

            // Инициализация логгера и звуков
            logger = new LoggerUtil();
            soundUtil = new SoundUtil();

            // Настройка трея
            setupTrayIcon();

            // Запуск таймера
            startTimer();
        });
    }

    private static void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            debugLog("System tray не поддерживается!");
            JOptionPane.showMessageDialog(null, "System tray не поддерживается на вашей системе.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image image = null;
        try {
            image = createTrayIconImage();
            if (image == null) {
                throw new IOException("Иконка не загружена.");
            }
        } catch (Exception e) {
            debugLog("Ошибка при загрузке иконки: " + e.getMessage());
            // Создаём простую синюю окружность
            BufferedImage bufferedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setColor(Color.BLUE);
            g2d.fillOval(0, 0, 16, 16);
            g2d.dispose();
            image = bufferedImage;
        }

        trayMenu = new PopupMenu();

        // MenuItem для отображения таймера
        trayTimerMenuItem = new MenuItem("Таймер: 00:00 / 00:25");
        trayMenu.add(trayTimerMenuItem);

        trayMenu.addSeparator();

        // Подменю для настроек
        Menu settingsMenu = new Menu("Настройки");

        // Подменю для выбора задачи
        Menu taskMenu = new Menu("Название задачи");
        String[] taskNames = {"Задача 1", "Задача 2", "Задача 3"};
        for (String taskName : taskNames) {
            MenuItem taskItem = new MenuItem(taskName);
            taskItem.addActionListener(e -> updateTaskName(taskName));
            taskMenu.add(taskItem);
        }
        settingsMenu.add(taskMenu);

        // Подменю для времени работы
        Menu workDurationMenu = new Menu("Время работы (мин)");
        int[] workDurations = {15, 25, 50};
        for (int duration : workDurations) {
            MenuItem durationItem = new MenuItem(String.valueOf(duration));
            durationItem.addActionListener(e -> updateWorkDuration(duration));
            workDurationMenu.add(durationItem);
        }
        settingsMenu.add(workDurationMenu);

        // Подменю для времени перерыва
        Menu breakDurationMenu = new Menu("Время перерыва (мин)");
        int[] breakDurations = {5, 10, 15};
        for (int duration : breakDurations) {
            MenuItem durationItem = new MenuItem(String.valueOf(duration));
            durationItem.addActionListener(e -> updateBreakDuration(duration));
            breakDurationMenu.add(durationItem);
        }
        settingsMenu.add(breakDurationMenu);

        trayMenu.add(settingsMenu);

        trayMenu.addSeparator();

        // MenuItem для управления таймером
        MenuItem pauseItem = new MenuItem("Пауза");
        pauseItem.addActionListener(e -> togglePause());
        trayMenu.add(pauseItem);

        MenuItem resetItem = new MenuItem("Сброс");
        resetItem.addActionListener(e -> resetTimer());
        trayMenu.add(resetItem);

        trayMenu.addSeparator();

        // MenuItem для статистики
        MenuItem statsItem = new MenuItem("Статистика");
        statsItem.addActionListener(e -> showStatistics());
        trayMenu.add(statsItem);

        trayMenu.addSeparator();

        // MenuItem для выхода из приложения
        MenuItem exitItem = new MenuItem("Выход");
        exitItem.addActionListener(e -> {
            tray.remove(trayIcon);
            System.exit(0);
        });
        trayMenu.add(exitItem);

        trayIcon = new TrayIcon(image, "Таймер", trayMenu);
        trayIcon.setImageAutoSize(true);

        try {
            tray.add(trayIcon);
            debugLog("Tray icon добавлен.");
        } catch (AWTException e) {
            debugLog("Не удалось добавить Tray icon: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Не удалось добавить Tray icon.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void startTimer() {
        swingTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isPaused) {
                    elapsedSeconds++;
                    remainingSeconds--;

                    // Обновление Tray MenuItem
                    updateTrayTimer(elapsedSeconds, remainingSeconds);

                    // Проверка окончания периода
                    if (remainingSeconds <= 0) {
                        swingTimer.stop();
                        handlePeriodEnd();
                    }
                }
            }
        });
        swingTimer.start();
    }

    private static void handlePeriodEnd() {
        // Логирование окончания периода
        String action = isWorkPeriod ? "END_WORK" : "END_BREAK";
        logger.log(currentTaskName, action, elapsedSeconds);

        // Воспроизведение звука
        soundUtil.playSound(isWorkPeriod ? "resources/end_work.wav" : "resources/end_break.wav");

        // Отправка уведомления
        if (isWorkPeriod) {
            sendNotification("Перерыв", "Рабочий период завершён. Время перерыва: " + breakDuration + " минут.");
            // Переключение на перерыв
            isWorkPeriod = false;
            remainingSeconds = breakDuration * 60;
            elapsedSeconds = 0;
            logger.log(currentTaskName, "START_BREAK", 0);
            soundUtil.playSound("resources/start_break.wav");
            sendNotification("Начало перерыва", "Начался перерыв: " + breakDuration + " минут.");
        } else {
            sendNotification("Работа", "Перерыв завершён. Возвращайтесь к задаче: " + currentTaskName + ".");
            // Переключение на работу
            isWorkPeriod = true;
            remainingSeconds = workDuration * 60;
            elapsedSeconds = 0;
            logger.log(currentTaskName, "START_WORK", 0);
            soundUtil.playSound("resources/start_work.wav");
            sendNotification("Начало работы", "Началась задача: " + currentTaskName + ". Время работы: " + workDuration + " минут.");
        }

        // Обновление Tray MenuItem
        updateTrayTimer(elapsedSeconds, remainingSeconds);

        // Запуск таймера снова
        swingTimer.start();
    }

    private static void updateTrayTimer(int elapsed, int remaining) {
        String displayTime = formatTime(elapsed);
        String remainingTime = formatTime(remaining);
        trayTimerMenuItem.setLabel("Таймер: " + displayTime + " / " + remainingTime);
    }

    private static void sendNotification(String title, String message) {
        String osName = System.getProperty("os.name").toLowerCase();
        debugLog("Отправка уведомления: " + title + " - " + message);
        try {
            if (osName.contains("mac")) {
                // Используем terminal-notifier для macOS
                String[] cmd = {
                        "terminal-notifier",
                        "-title", title,
                        "-message", message
                };
                Process process = Runtime.getRuntime().exec(cmd);
                process.waitFor();
            } else if (osName.contains("win")) {
                // Используем PowerShell для Windows
                String command = "powershell.exe -Command \"[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] > $null; " +
                        "$template = [Windows.UI.Notifications.ToastTemplateType]::ToastText02; " +
                        "$toastXml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent($template); " +
                        "$toastXml.GetElementsByTagName('text').Item(0).AppendChild($toastXml.CreateTextNode('" + title + "')); " +
                        "$toastXml.GetElementsByTagName('text').Item(1).AppendChild($toastXml.CreateTextNode('" + message + "')); " +
                        "$toast = [Windows.UI.Notifications.ToastNotification]::new($toastXml); " +
                        "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Java Timer App').Show($toast)\"";
                Runtime.getRuntime().exec(command);
            } else {
                // Для Linux используем notify-send
                String[] cmd = {"notify-send", title, message};
                Process process = Runtime.getRuntime().exec(cmd);
                process.waitFor();
            }
        } catch (IOException | InterruptedException e) {
            debugLog("Ошибка при отправке уведомления: " + e.getMessage());
        }
    }

    public static void togglePause() {
        isPaused = !isPaused;
        String status = isPaused ? "Пауза" : "Продолжить";
        trayMenu.getItem(2).setLabel(isPaused ? "Продолжить" : "Пауза");
        logger.log(currentTaskName, isPaused ? "PAUSE" : "RESUME", elapsedSeconds);
        soundUtil.playSound(isPaused ? "resources/pause.wav" : "resources/resume.wav");
        sendNotification(isPaused ? "Пауза" : "Продолжение", isPaused ? "Таймер поставлен на паузу." : "Таймер возобновлён.");
    }

    public static void resetTimer() {
        swingTimer.stop();
        elapsedSeconds = 0;
        remainingSeconds = isWorkPeriod ? workDuration * 60 : breakDuration * 60;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        isPaused = false;
        trayMenu.getItem(2).setLabel("Пауза");
        logger.log(currentTaskName, "RESET", 0);
        soundUtil.playSound("resources/reset.wav");
        sendNotification("Сброс", "Таймер сброшен.");
        swingTimer.start();

        // Сохранение настроек после сброса
        saveConfig();
    }

    public static void updateSettings(String taskName, int newWorkDuration, int newBreakDuration) {
        currentTaskName = taskName;
        workDuration = newWorkDuration;
        breakDuration = newBreakDuration;
        remainingSeconds = isWorkPeriod ? workDuration * 60 : breakDuration * 60;
        elapsedSeconds = 0;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        logger.log(currentTaskName, "UPDATE_SETTINGS", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Настройки", "Настройки обновлены.");

        // Сохранение настроек
        saveConfig();
    }

    private static void saveConfig() {
        JSONObject config = new JSONObject();
        config.put("taskName", currentTaskName);
        config.put("workDuration", workDuration);
        config.put("breakDuration", breakDuration);
        ConfigUtil.saveConfig(config);
    }

    private static void updateTaskName(String taskName) {
        currentTaskName = taskName;
        elapsedSeconds = 0;
        remainingSeconds = isWorkPeriod ? workDuration * 60 : breakDuration * 60;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        logger.log(currentTaskName, "UPDATE_TASK_NAME", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Название задачи обновлено", "Текущая задача: " + currentTaskName);
        saveConfig();
    }

    private static void updateWorkDuration(int duration) {
        workDuration = duration;
        if (isWorkPeriod) {
            remainingSeconds = workDuration * 60;
            elapsedSeconds = 0;
            updateTrayTimer(elapsedSeconds, remainingSeconds);
        }
        logger.log(currentTaskName, "UPDATE_WORK_DURATION", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Время работы обновлено", "Новое время работы: " + workDuration + " минут");
        saveConfig();
    }

    private static void updateBreakDuration(int duration) {
        breakDuration = duration;
        if (!isWorkPeriod) {
            remainingSeconds = breakDuration * 60;
            elapsedSeconds = 0;
            updateTrayTimer(elapsedSeconds, remainingSeconds);
        }
        logger.log(currentTaskName, "UPDATE_BREAK_DURATION", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Время перерыва обновлено", "Новое время перерыва: " + breakDuration + " минут");
        saveConfig();
    }

    private static String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private static void debugLog(String message) {
        if (DEBUG_MODE) {
            System.out.println("[DEBUG] " + message);
        }
    }

    private static Image createTrayIconImage() {
        try {
            return ImageIO.read(new File("resources/selftimer_23880.png"));
        } catch (IOException e) {
            debugLog("Ошибка при загрузке иконки: " + e.getMessage());
            // Создаём простую синюю окружность
            BufferedImage bufferedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setColor(Color.BLUE);
            g2d.fillOval(0, 0, 16, 16);
            g2d.dispose();
            return bufferedImage;
        }
    }

    private static void showStatistics() {
        String stats = getTodayStatistics();
        sendNotification("Статистика за сегодня", stats);
    }

    private static String getTodayStatistics() {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String logFilePath = "./logs/log_" + currentDate + ".jsonl"; // Используем .jsonl

        File logFile = new File(logFilePath);
        if (!logFile.exists()) {
            return "Нет данных за сегодня.";
        }

        try {
            List<String> lines = Files.readAllLines(logFile.toPath());

            int totalWorkSeconds = 0;
            int totalBreakSeconds = 0;
            String currentPeriod = "unknown"; // "work", "break", "unknown"

            for (String line : lines) {
                if (line.trim().isEmpty()) continue; // Пропуск пустых строк
                JSONObject entry = new JSONObject(line);
                String action = entry.getString("action");
                int duration = entry.getInt("duration_seconds");

                switch (action) {
                    case "START_WORK":
                        currentPeriod = "work";
                        break;
                    case "START_BREAK":
                        currentPeriod = "break";
                        break;
                    case "END_WORK":
                    case "PAUSE":
                    case "RESET":
                        if (currentPeriod.equals("work")) {
                            totalWorkSeconds += duration;
                            if (action.equals("RESET")) {
                                currentPeriod = "unknown";
                            } else {
                                currentPeriod = "unknown"; // После PAUSE и END_WORK переходим в unknown
                            }
                        }
                        break;
                    case "END_BREAK":
                        if (currentPeriod.equals("break")) {
                            totalBreakSeconds += duration;
                            if (action.equals("RESET")) {
                                currentPeriod = "unknown";
                            } else {
                                currentPeriod = "unknown"; // После PAUSE и END_BREAK переходим в unknown
                            }
                        }
                        break;
                    // Дополнительные действия можно обработать здесь
                    default:
                        // Игнорируем другие действия, например, UPDATE_TASK_NAME
                        break;
                }
            }

            String workTime = formatTime(totalWorkSeconds);
            String breakTime = formatTime(totalBreakSeconds);

            return "Время работы: " + workTime + "\nВремя перерывов: " + breakTime;

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return "Ошибка при получении статистики.";
        }
    }
}