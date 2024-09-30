//package src;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.util.List;
//
//public class TimerFrame extends JFrame {
//    private JLabel taskLabel;
//    private JLabel stateLabel;
//    private JLabel timeLabel;
//    private JButton pauseButton;
//    private JButton resetButton;
//    public JComboBox<String> taskComboBox;
//    private JButton updateTaskButton;
//    private JProgressBar progressBar;
//
//    public TimerFrame() {
//        setTitle("Pomodoro Timer");
//        setSize(400, 300);
//        setLayout(new GridLayout(6, 1));
//        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//        setLocationRelativeTo(null);
//        setAlwaysOnTop(true);
//
//        taskLabel = new JLabel("Задача: " + TimerApp.currentTaskName, SwingConstants.CENTER);
//        stateLabel = new JLabel("Состояние: Работа", SwingConstants.CENTER);
//        timeLabel = new JLabel("Осталось: 25:00", SwingConstants.CENTER);
//        timeLabel.setFont(new Font("Arial", Font.BOLD, 24));
//
//        progressBar = new JProgressBar(0, TimerApp.workDuration * 60);
//        progressBar.setValue(0);
//        progressBar.setStringPainted(true);
//
//        JPanel taskPanel = new JPanel();
//        taskPanel.setLayout(new BorderLayout());
//        taskPanel.add(new JLabel("Выбрать задачу:"), BorderLayout.WEST);
//        taskComboBox = new JComboBox<>(TimerApp.taskNames.toArray(new String[0]));
//        taskPanel.add(taskComboBox, BorderLayout.CENTER);
//        updateTaskButton = new JButton("Обновить задачу");
//        taskPanel.add(updateTaskButton, BorderLayout.EAST);
//
//        JPanel buttonPanel = new JPanel();
//        pauseButton = new JButton("Пауза");
//        resetButton = new JButton("Сброс");
//        buttonPanel.add(pauseButton);
//        buttonPanel.add(resetButton);
//
//        add(taskLabel);
//        add(stateLabel);
//        add(timeLabel);
//        add(progressBar);
//        add(taskPanel);
//        add(buttonPanel);
//
//        // Обработчики кнопок
//        pauseButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                TimerApp.togglePause();
//                updateButtons();
//            }
//        });
//
//        resetButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                TimerApp.resetTimer();
//                updateDisplay();
//                updateButtons();
//            }
//        });
//
//        updateTaskButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                String selectedTask = (String) taskComboBox.getSelectedItem();
//                if (selectedTask != null) {
//                    TimerApp.updateTaskName(selectedTask);
//                    updateDisplay();
//                }
//            }
//        });
//    }
//
//    /**
//     * Обновляет список задач в выпадающем списке.
//     */
//    public void updateTaskList() {
//        taskComboBox.removeAllItems();
//        for (String task : TimerApp.taskNames) {
//            taskComboBox.addItem(task);
//        }
//    }
//
//    /**
//     * Обновляет отображение информации в окне.
//     */
//    public void updateDisplay() {
//        taskLabel.setText("Задача: " + TimerApp.currentTaskName);
//        stateLabel.setText("Состояние: " + stateToString(TimerApp.currentState));
//        String remainingTime = TimerApp.formatTime(TimerApp.remainingSeconds);
//        timeLabel.setText("Осталось: " + remainingTime);
//
//        // Обновление прогресс-бара
//        int totalSeconds;
//        switch (TimerApp.currentState) {
//            case WORK:
//                totalSeconds = TimerApp.workDuration * 60;
//                break;
//            case BREAK:
//                totalSeconds = TimerApp.breakDuration * 60;
//                break;
//            case LONG_BREAK:
//                totalSeconds = TimerApp.breakDuration * 2 * 60;
//                break;
//            default:
//                totalSeconds = TimerApp.workDuration * 60;
//        }
//        progressBar.setMaximum(totalSeconds);
//        int elapsed = totalSeconds - TimerApp.remainingSeconds;
//        progressBar.setValue(elapsed);
//        progressBar.setString(String.format("%.0f%%", (elapsed * 100.0) / totalSeconds));
//    }
//
//    private String stateToString(TimerApp.TimerState state) {
//        switch (state) {
//            case WORK:
//                return "Работа";
//            case BREAK:
//                return "Перерыв";
//            case LONG_BREAK:
//                return "Длительный перерыв";
//            case PAUSED:
//                return "Пауза";
//            default:
//                return "Неизвестно";
//        }
//    }
//
//    private void updateButtons() {
//        if (TimerApp.isPaused) {
//            pauseButton.setText("Продолжить");
//        } else {
//            pauseButton.setText("Пауза");
//        }
//    }
//}
