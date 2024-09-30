// javac Timer/TimerAppOne.java && java Timer.TimerAppOne "Название задачи" 25 5

package Timer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
public class TimerAppOne {
    private static boolean DEBUG_MODE = true; // Включаем режим отладки для диагностики
    private static String TASK_NAME;
    private static int WORK_DURATION; // Время работы в минутах
    private static int BREAK_DURATION; // Время перерыва в минутах
    private static String LOG_FILE;
    private static TrayIcon trayIcon;
    private static MenuItem timerMenuItem;
    private static TimerWindowOne timerWindow;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Использование: Timer.TimerApp \"Название задачи\" <время работы в минутах> <время перерыва в минутах>");
            System.exit(1);
        }

        TASK_NAME = args[0];
        try {
            WORK_DURATION = Integer.parseInt(args[1]);
            BREAK_DURATION = Integer.parseInt(args[2]);
            if (WORK_DURATION <= 0 || BREAK_DURATION <= 0) {
                throw new NumberFormatException("Время работы и перерыва должны быть положительными числами.");
            }
        } catch (NumberFormatException e) {
            System.err.println("Ошибка: Время работы и перерыва должны быть положительными целыми числами.");
            System.exit(1);
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        LOG_FILE = "./logs/log_" + currentDate + ".csv";

        setupTrayIcon();
        logToFile("START_OF_DAY", 0, TASK_NAME);

        SwingUtilities.invokeLater(() -> {
            timerWindow = new TimerWindowOne(TASK_NAME);
            timerWindow.setVisible(true);
        });

        // Основной цикл: работа -> перерыв
        while (true) {
            sendNotification("Начало работы", "Началась задача: " + TASK_NAME + ". Время работы: " + WORK_DURATION + " минут.");
            timer(WORK_DURATION * 60, true);

            sendNotification("Перерыв", "Начался перерыв по задаче: " + TASK_NAME + ". Время перерыва: " + BREAK_DURATION + " минут.");
            timer(BREAK_DURATION * 60, false);

            sendNotification("Конец перерыва", "Перерыв завершён. Возвращайтесь к задаче: " + TASK_NAME + ".");
        }
    }

    private static void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray is not supported!");
            return;
        }

        // Создаём иконку для трея
        Image image = Toolkit.getDefaultToolkit().getImage("resources/selftimer_23880.png");
        if (image == null) {
            // Если не удалось загрузить изображение, создаём простую
            BufferedImage bufferedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setColor(Color.BLUE);
            g2d.fillOval(0, 0, 16, 16);
            g2d.dispose();
            image = bufferedImage;
        }

        // Создаём PopupMenu с MenuItem для таймера
        PopupMenu popup = new PopupMenu();
        timerMenuItem = new MenuItem("Таймер: 00:00:00 / 00:00:00");
        popup.add(timerMenuItem);

        // Добавляем разделитель и пункт "Выход"
        popup.addSeparator();
        MenuItem exitItem = new MenuItem("Выход");
        exitItem.addActionListener(e -> {
            System.exit(0);
        });
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "Таймер", popup);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();
        try {
            tray.add(trayIcon);
            debugLog("Tray icon добавлен.");
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private static void sendNotification(String title, String message) {
        String osName = System.getProperty("os.name").toLowerCase();
        debugLog("Отправка уведомления: " + title + " - " + message);
        if (osName.contains("mac")) {
            // Используем terminal-notifier для отправки уведомления на macOS
            String[] cmd = {
                    "terminal-notifier",
                    "-title", title,
                    "-message", message
            };
            try {
                Process process = Runtime.getRuntime().exec(cmd);
                process.waitFor(); // Ждём завершения процесса
                if (process.exitValue() != 0) {
                    debugLog("Ошибка при отправке уведомления через terminal-notifier.");
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } else if (SystemTray.isSupported()) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        } else {
            System.out.println("Уведомление: " + title + " - " + message);
        }
    }

    private static void logToFile(String action, long duration, String taskName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            writer.write(timestamp + ", " + taskName + ", " + action + ", " + duration);
            writer.newLine();
            debugLog("Запись в лог: " + timestamp + ", " + taskName + ", " + action + ", " + duration);
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог файл: " + e.getMessage());
        }
    }

    private static void timer(int duration, boolean isWork) {
        long elapsed = 0;
        long remaining = duration;

        while (elapsed < duration) {
            String displayTime = String.format("%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
            String remainingTime = String.format("%02d:%02d:%02d", remaining / 3600, (remaining % 3600) / 60, remaining % 60);

            // Обновляем вывод в терминале
            System.out.print("\r" + TASK_NAME + " - Затрачено времени: " + displayTime + ", до перерыва: " + remainingTime);

            // Обновляем текст в окне
            final String windowText = "<html>" + TASK_NAME + " - " + (isWork ? "Работа" : "Перерыв") +
                    "<br>Затрачено: " + displayTime +
                    "<br>Осталось: " + remainingTime + "</html>";
            SwingUtilities.invokeLater(() -> {
                if (timerWindow != null) {
                    timerWindow.updateTime(windowText);
                }
            });

            // Обновляем таймер в трее
            final String trayText = "Таймер: " + displayTime + " / " + remainingTime;
            SwingUtilities.invokeLater(() -> {
                if (timerMenuItem != null) {
                    timerMenuItem.setLabel(trayText);
                }
            });

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Выходим из цикла при прерывании
            }
            elapsed++;
            remaining--;
        }
        System.out.println(); // Добавляем новую строку после завершения таймера
        logToFile(isWork ? "END_WORK" : "END_BREAK", elapsed, TASK_NAME);
    }

    private static void debugLog(String message) {
        if (DEBUG_MODE) {
            System.out.println("[DEBUG] " + message);
        }
    }
}

// Класс для отображения таймера в окне
class TimerWindowOne extends JFrame {
    private JLabel label;

    public TimerWindowOne(String taskName) {
        setTitle("Таймер: " + taskName);
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setAlwaysOnTop(true); // Окно всегда поверх других
        label = new JLabel("", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.PLAIN, 16));
        add(label);
        setLocationRelativeTo(null);
    }

    public void updateTime(String text) {
        label.setText(text);
    }
}