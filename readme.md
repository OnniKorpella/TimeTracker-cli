# README

## Script Description

This bash script is designed to manage work tasks and breaks, providing a simple and convenient way to track work and rest time. It follows the Pomodoro technique (or similar methods), allowing you to set the duration of work and break periods for each task. The script logs tasks with timestamps in CSV format, sends notifications, and displays work and break time statistics at the end of each session.

## Before using:

```bash
brew install terminal-notifier
brew install miller
chmod +x timer.sh
```

## Usage

### Running the script

```bash
sh script.sh "Task Name" <work time in minutes> <break time in minutes>
```

### Arguments:
- `Task Name` — the name of the task that will be displayed in the logs and on the screen.
- `<work time in minutes>` — the duration of the work period in minutes.
- `<break time in minutes>` — the duration of the break period in minutes.

### Example:
```bash
./script.sh "Project Development" 45 15
```
This will start the task "Project Development" with a work period of 45 minutes and a break period of 15 minutes.

## Logging

- Logs are saved in the `./logs` directory.
- Log files are created with the name `log_YYYY-MM-DD.csv`, where `YYYY-MM-DD` is the current date.
- Log format: `HH:MM:SS, TASK_NAME, ACTION, DURATION`, where `ACTION` can be:
  - `START_WORK` — the start of the work period
  - `END_WORK` — the end of the work period
  - `START_BREAK` — the start of the break
  - `END_BREAK` — the end of the break

## Notification Support

The script supports sending notifications using `terminal-notifier` (macOS) or `notify-send` (Linux). If neither tool is installed, notifications will be displayed in the terminal.

## Example Output

```bash
Task: Project Development
  Total Work Time:    01:30:00
  Total Break Time:   00:30:00
```

## Requirements

- Bash
- Notifications: `terminal-notifier` for macOS or `notify-send` for Linux (optional).

## Exiting the Script

To stop the script, use the `Ctrl+C` keyboard shortcut. The script will automatically save the current data and display statistics.