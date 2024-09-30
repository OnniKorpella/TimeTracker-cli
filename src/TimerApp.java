package src;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    // Текущая задача и настройки
    private static JSONObject currentTask;
    private static int workDuration; // минуты
    private static int breakDuration; // минуты

    // Настройки Pomodoro
    private static int cyclesBeforeLongBreak;
    private static int longBreakDuration; // минуты
    private static int pomodoroCycleCount = 0;

    // Состояние таймера
    private static boolean isPaused = false;

    private enum TimerState {
        WORK,
        SHORT_BREAK,
        LONG_BREAK
    }

    private static TimerState timerState = TimerState.WORK;

    private static Timer swingTimer;
    private static int elapsedSeconds = 0;
    private static int remainingSeconds;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            debugLog("Запуск приложения...");
            // Загрузка конфигурации
            loadConfigurations();

            // Инициализация логгера и звуков
            logger = new LoggerUtil();
            soundUtil = new SoundUtil();

            // Настройка трея
            setupTrayIcon();

            // Запуск таймера
            startTimer();
        });
    }

    private static void loadConfigurations() {
        // Загрузка текущей задачи
        currentTask = ConfigUtil.getCurrentTask();
        workDuration = currentTask.getInt("workDuration");
        breakDuration = currentTask.getInt("breakDuration");

        // Загрузка настроек Pomodoro
        JSONObject pomodoroSettings = ConfigUtil.loadPomodoroSettings();
        cyclesBeforeLongBreak = pomodoroSettings.getInt("cyclesBeforeLongBreak");
        longBreakDuration = pomodoroSettings.getInt("longBreakDuration");

        // Инициализация состояния таймера
        timerState = TimerState.WORK;
        remainingSeconds = workDuration * 60;
        elapsedSeconds = 0;
        pomodoroCycleCount = 0;
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
        trayTimerMenuItem = new MenuItem("Таймер: 00:00 / " + formatTime(workDuration * 60));
        trayMenu.add(trayTimerMenuItem);

        trayMenu.addSeparator();

        // Подменю для настроек
        Menu settingsMenu = new Menu("Настройки");

        // Подменю для выбора задачи
        Menu taskMenu = new Menu("Название задачи");
        List<JSONObject> tasks = ConfigUtil.loadTasks();
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.get(i);
            String taskName = task.getString("name");
            MenuItem taskItem = new MenuItem(taskName);
            int taskIndex = i; // Для использования внутри лямбды
            taskItem.addActionListener(e -> updateTask(taskIndex));
            taskMenu.add(taskItem);
        }

        // Добавить кнопку для добавления новой задачи
        MenuItem addTaskItem = new MenuItem("Добавить задачу");
        addTaskItem.addActionListener(e -> showAddTaskDialog());
        taskMenu.addSeparator();
        taskMenu.add(addTaskItem);
        settingsMenu.add(taskMenu);

        // Подменю для времени работы
        Menu workDurationMenu = new Menu("Время работы (мин)");
        int[] workDurations = {15, 25, 50};
        for (int duration : workDurations) {
            MenuItem durationItem = new MenuItem(String.valueOf(duration));
            durationItem.addActionListener(e -> updateWorkDuration(duration));
            workDurationMenu.add(durationItem);
        }
        // Добавление возможности пользовательского ввода
        MenuItem customWorkDurationItem = new MenuItem("Другое...");
        customWorkDurationItem.addActionListener(e -> showCustomWorkDurationDialog());
        workDurationMenu.addSeparator();
        workDurationMenu.add(customWorkDurationItem);
        settingsMenu.add(workDurationMenu);

        // Подменю для времени перерыва
        Menu breakDurationMenu = new Menu("Время перерыва (мин)");
        int[] breakDurations = {5, 10, 15};
        for (int duration : breakDurations) {
            MenuItem durationItem = new MenuItem(String.valueOf(duration));
            durationItem.addActionListener(e -> updateBreakDuration(duration));
            breakDurationMenu.add(durationItem);
        }
        // Добавление возможности пользовательского ввода
        MenuItem customBreakDurationItem = new MenuItem("Другое...");
        customBreakDurationItem.addActionListener(e -> showCustomBreakDurationDialog());
        breakDurationMenu.addSeparator();
        breakDurationMenu.add(customBreakDurationItem);
        settingsMenu.add(breakDurationMenu);

        // Подменю для настроек Pomodoro
        Menu pomodoroSettingsMenu = new Menu("Настройки Pomodoro");

        // Пункт для установки количества циклов перед длинным перерывом
        MenuItem setCyclesItem = new MenuItem("Циклов перед длинным перерывом");
        setCyclesItem.addActionListener(e -> showSetCyclesDialog());
        pomodoroSettingsMenu.add(setCyclesItem);

        // Пункт для установки длительности длинного перерыва
        MenuItem setLongBreakItem = new MenuItem("Длительность длинного перерыва (мин)");
        setLongBreakItem.addActionListener(e -> showSetLongBreakDialog());
        pomodoroSettingsMenu.add(setLongBreakItem);

        settingsMenu.add(pomodoroSettingsMenu);

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
        String action = "";
        if (timerState == TimerState.WORK) {
            action = "END_WORK";
        } else if (timerState == TimerState.SHORT_BREAK) {
            action = "END_BREAK";
        } else if (timerState == TimerState.LONG_BREAK) {
            action = "END_LONG_BREAK";
        }
        logger.log(currentTask.getString("name"), action, elapsedSeconds);

        // Воспроизведение звука
        String soundPath = "";
        if (timerState == TimerState.WORK) {
            soundPath = "resources/end_work.wav";
        } else if (timerState == TimerState.SHORT_BREAK) {
            soundPath = "resources/end_break.wav";
        } else if (timerState == TimerState.LONG_BREAK) {
            soundPath = "resources/end_long_break.wav";
        }
        soundUtil.playSound(soundPath);

        // Отправка уведомления и переключение периода
        if (timerState == TimerState.WORK) {
            pomodoroCycleCount++;
            if (pomodoroCycleCount >= cyclesBeforeLongBreak) {
                // Начало длинного перерыва
                timerState = TimerState.LONG_BREAK;
                remainingSeconds = longBreakDuration * 60;
                pomodoroCycleCount = 0; // Сброс счетчика
                logger.log(currentTask.getString("name"), "START_LONG_BREAK", 0);
                soundUtil.playSound("resources/start_long_break.wav");
                sendNotification("Длинный Перерыв", "Начался длинный перерыв: " + longBreakDuration + " минут.");
            } else {
                // Начало короткого перерыва
                timerState = TimerState.SHORT_BREAK;
                remainingSeconds = breakDuration * 60;
                logger.log(currentTask.getString("name"), "START_BREAK", 0);
                soundUtil.playSound("resources/start_break.wav");
                sendNotification("Перерыв", "Начался перерыв: " + breakDuration + " минут.");
            }
        } else {
            // Начало рабочего периода
            timerState = TimerState.WORK;
            remainingSeconds = workDuration * 60;
            elapsedSeconds = 0;
            logger.log(currentTask.getString("name"), "START_WORK", 0);
            soundUtil.playSound("resources/start_work.wav");
            sendNotification("Начало Работы", "Началась задача: " + currentTask.getString("name") + ". Время работы: " + workDuration + " минут.");
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
        // Найти соответствующий MenuItem и обновить его метку
        for (int i = 0; i < trayMenu.getItemCount(); i++) {
            MenuItem item = trayMenu.getItem(i);
            if (item.getLabel().equals("Пауза") || item.getLabel().equals("Продолжить")) {
                item.setLabel(isPaused ? "Продолжить" : "Пауза");
                break;
            }
        }
        logger.log(currentTask.getString("name"), isPaused ? "PAUSE" : "RESUME", elapsedSeconds);
        soundUtil.playSound(isPaused ? "resources/pause.wav" : "resources/resume.wav");
        sendNotification(isPaused ? "Пауза" : "Продолжение", isPaused ? "Таймер поставлен на паузу." : "Таймер возобновлён.");
    }

    public static void resetTimer() {
        swingTimer.stop();
        elapsedSeconds = 0;
        remainingSeconds = (timerState == TimerState.WORK) ? workDuration * 60 :
                (timerState == TimerState.SHORT_BREAK) ? breakDuration * 60 :
                        longBreakDuration * 60;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        isPaused = false;
        // Обновить метку "Пауза" на начальное состояние
        for (int i = 0; i < trayMenu.getItemCount(); i++) {
            MenuItem item = trayMenu.getItem(i);
            if (item.getLabel().equals("Пауза") || item.getLabel().equals("Продолжить")) {
                item.setLabel("Пауза");
                break;
            }
        }
        logger.log(currentTask.getString("name"), "RESET", 0);
        soundUtil.playSound("resources/reset.wav");
        sendNotification("Сброс", "Таймер сброшен.");
        swingTimer.start();

        // Сохранение настроек после сброса
        saveConfig();
    }

    public static void updateSettings(String taskName, int newWorkDuration, int newBreakDuration) {
        currentTask = ConfigUtil.getCurrentTask();
        currentTask.put("name", taskName);
        currentTask.put("workDuration", newWorkDuration);
        currentTask.put("breakDuration", newBreakDuration);
        workDuration = newWorkDuration;
        breakDuration = newBreakDuration;
        remainingSeconds = (timerState == TimerState.WORK) ? workDuration * 60 :
                (timerState == TimerState.SHORT_BREAK) ? breakDuration * 60 :
                        longBreakDuration * 60;
        elapsedSeconds = 0;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        logger.log(taskName, "UPDATE_SETTINGS", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Настройки", "Настройки обновлены.");

        // Сохранение настроек
        saveConfig();
    }

    private static void saveConfig() {
        JSONObject config = new JSONObject();
        config.put("tasks", ConfigUtil.loadConfig().getJSONArray("tasks"));
        config.put("currentTaskIndex", ConfigUtil.loadConfig().getInt("currentTaskIndex"));
        JSONObject pomodoroSettings = new JSONObject();
        pomodoroSettings.put("cyclesBeforeLongBreak", cyclesBeforeLongBreak);
        pomodoroSettings.put("longBreakDuration", longBreakDuration);
        config.put("pomodoroSettings", pomodoroSettings);
        ConfigUtil.saveConfig(config);
    }

    private static void updateTask(int taskIndex) {
        ConfigUtil.setCurrentTaskIndex(taskIndex);
        currentTask = ConfigUtil.getCurrentTask();
        workDuration = currentTask.getInt("workDuration");
        breakDuration = currentTask.getInt("breakDuration");
        remainingSeconds = (timerState == TimerState.WORK) ? workDuration * 60 :
                (timerState == TimerState.SHORT_BREAK) ? breakDuration * 60 :
                        longBreakDuration * 60;
        elapsedSeconds = 0;
        updateTrayTimer(elapsedSeconds, remainingSeconds);
        logger.log(currentTask.getString("name"), "UPDATE_TASK", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Задача обновлена", "Текущая задача: " + currentTask.getString("name"));
        saveConfig();
    }

    private static void updateWorkDuration(int duration) {
        workDuration = duration;
        if (timerState == TimerState.WORK) {
            remainingSeconds = workDuration * 60;
            elapsedSeconds = 0;
            updateTrayTimer(elapsedSeconds, remainingSeconds);
        }
        currentTask.put("workDuration", workDuration);
        logger.log(currentTask.getString("name"), "UPDATE_WORK_DURATION", 0);
        soundUtil.playSound("resources/settings.wav");
        sendNotification("Время работы обновлено", "Новое время работы: " + workDuration + " минут");
        saveConfig();
    }

    private static void updateBreakDuration(int duration) {
        breakDuration = duration;
        if (timerState == TimerState.SHORT_BREAK) {
            remainingSeconds = breakDuration * 60;
            elapsedSeconds = 0;
            updateTrayTimer(elapsedSeconds, remainingSeconds);
        }
        currentTask.put("breakDuration", breakDuration);
        logger.log(currentTask.getString("name"), "UPDATE_BREAK_DURATION", 0);
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
                    case "START_LONG_BREAK":
                        currentPeriod = "break";
                        break;
                    case "END_WORK":
                    case "PAUSE":
                    case "RESET":
                        if (currentPeriod.equals("work")) {
                            totalWorkSeconds += duration;
                            currentPeriod = "unknown"; // После PAUSE и END_WORK переходим в unknown
                        }
                        break;
                    case "END_BREAK":
                        if (currentPeriod.equals("break")) {
                            totalBreakSeconds += duration;
                            currentPeriod = "unknown"; // После PAUSE и END_BREAK переходим в unknown
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

    // Методы для добавления задач и настроек
    private static void showAddTaskDialog() {
        JTextField taskNameField = new JTextField();
        JTextField workDurationField = new JTextField();
        JTextField breakDurationField = new JTextField();

        Object[] message = {
                "Название задачи:", taskNameField,
                "Время работы (мин):", workDurationField,
                "Время перерыва (мин):", breakDurationField
        };

        int option = JOptionPane.showConfirmDialog(null, message, "Добавить Задачу", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String taskName = taskNameField.getText().trim();
            String workDurationStr = workDurationField.getText().trim();
            String breakDurationStr = breakDurationField.getText().trim();

            if (taskName.isEmpty() || workDurationStr.isEmpty() || breakDurationStr.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Все поля обязательны для заполнения.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                int workDur = Integer.parseInt(workDurationStr);
                int breakDur = Integer.parseInt(breakDurationStr);

                if (workDur <= 0 || breakDur <= 0) {
                    JOptionPane.showMessageDialog(null, "Длительности должны быть положительными числами.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                JSONObject newTask = new JSONObject();
                newTask.put("name", taskName);
                newTask.put("workDuration", workDur);
                newTask.put("breakDuration", breakDur);

                List<JSONObject> tasks = ConfigUtil.loadTasks();
                tasks.add(newTask);
                ConfigUtil.saveTasks(tasks);

                // Обновить интерфейс меню задач
                rebuildTaskMenu();

                sendNotification("Задача добавлена", "Новая задача: " + taskName);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Время должно быть числом.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showSetCyclesDialog() {
        String input = JOptionPane.showInputDialog(null, "Введите количество циклов перед длинным перерывом:", cyclesBeforeLongBreak);
        if (input != null) {
            try {
                int cycles = Integer.parseInt(input.trim());
                if (cycles > 0) {
                    cyclesBeforeLongBreak = cycles;
                    logger.log(currentTask.getString("name"), "UPDATE_CYCLES_BEFORE_LONG_BREAK", 0);
                    soundUtil.playSound("resources/settings.wav");
                    sendNotification("Настройки Pomodoro", "Количество циклов перед длинным перерывом установлено на " + cycles + ".");
                    saveConfig();
                } else {
                    JOptionPane.showMessageDialog(null, "Введите положительное число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Неверный формат числа.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showSetLongBreakDialog() {
        String input = JOptionPane.showInputDialog(null, "Введите длительность длинного перерыва (мин):", longBreakDuration);
        if (input != null) {
            try {
                int duration = Integer.parseInt(input.trim());
                if (duration > 0) {
                    longBreakDuration = duration;
                    logger.log(currentTask.getString("name"), "UPDATE_LONG_BREAK_DURATION", 0);
                    soundUtil.playSound("resources/settings.wav");
                    sendNotification("Настройки Pomodoro", "Длительность длинного перерыва установлена на " + duration + " минут.");
                    saveConfig();
                } else {
                    JOptionPane.showMessageDialog(null, "Введите положительное число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Неверный формат числа.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showCustomWorkDurationDialog() {
        String input = JOptionPane.showInputDialog(null, "Введите время работы (мин):", workDuration);
        if (input != null) {
            try {
                int duration = Integer.parseInt(input.trim());
                if (duration > 0) {
                    updateWorkDuration(duration);
                } else {
                    JOptionPane.showMessageDialog(null, "Введите положительное число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Неверный формат числа.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showCustomBreakDurationDialog() {
        String input = JOptionPane.showInputDialog(null, "Введите время перерыва (мин):", breakDuration);
        if (input != null) {
            try {
                int duration = Integer.parseInt(input.trim());
                if (duration > 0) {
                    updateBreakDuration(duration);
                } else {
                    JOptionPane.showMessageDialog(null, "Введите положительное число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Неверный формат числа.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void rebuildTaskMenu() {
        // Удаление старого меню задач
        for (int i = 0; i < trayMenu.getItemCount(); i++) {
            MenuItem item = trayMenu.getItem(i);
            if (item instanceof Menu && item.getLabel().equals("Название задачи")) {
                trayMenu.remove(i);
                break;
            }
        }

        // Создание нового меню задач
        Menu taskMenu = new Menu("Название задачи");
        List<JSONObject> tasks = ConfigUtil.loadTasks();
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.get(i);
            String taskName = task.getString("name");
            MenuItem taskItem = new MenuItem(taskName);
            int taskIndex = i; // Для использования внутри лямбды
            taskItem.addActionListener(e -> updateTask(taskIndex));
            taskMenu.add(taskItem);
        }

        // Добавить кнопку для добавления новой задачи
        MenuItem addTaskItem = new MenuItem("Добавить задачу");
        addTaskItem.addActionListener(e -> showAddTaskDialog());
        taskMenu.addSeparator();
        taskMenu.add(addTaskItem);

        // Добавление нового меню задач в trayMenu
        trayMenu.insert(taskMenu, 2); // Вставка после таймера и разделителя
    }
}