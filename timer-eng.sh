#!/bin/bash

DEBUG_MODE=false  # Debug mode switch

# Function to output debug information
debug_log() {
  if [ "$DEBUG_MODE" = true ]; then
    echo "[DEBUG] $1"
  fi
}

# Check for the required arguments
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 \"Task Name\" <work duration in minutes> <break duration in minutes>"
  exit 1
fi

# Variables
TASK_NAME=$1
WORK_DURATION=$2   # Work duration in minutes
BREAK_DURATION=$3  # Break duration in minutes
CURRENT_DATE=$(date +"%Y-%m-%d")  # Current date in YYYY-MM-DD format
LOG_DIR="./logs"  # Log directory
LOG_FILE="$LOG_DIR/log_${CURRENT_DATE}.csv"  # Log file name with date in CSV format

# Debug messages
debug_log "Starting task: $TASK_NAME"
debug_log "Work duration: $WORK_DURATION minutes"
debug_log "Break duration: $BREAK_DURATION minutes"
debug_log "Log file: $LOG_FILE"

# Colors
COLOR_SUPPORT=true
if [ -n "$NO_COLOR" ] || [ ! -t 1 ]; then
  COLOR_SUPPORT=false
fi

if $COLOR_SUPPORT; then
  GREEN="\033[0;32m"
  RED="\033[0;31m"
  YELLOW="\033[0;33m"
  BLUE="\033[0;34m"
  CYAN="\033[0;36m"
  RESET="\033[0m"  # Reset color
else
  GREEN=""
  RED=""
  YELLOW=""
  BLUE=""
  CYAN=""
  RESET=""
fi

# Check that positive integers are provided
if ! [[ "$WORK_DURATION" =~ ^[0-9]+$ ]] || ! [[ "$BREAK_DURATION" =~ ^[0-9]+$ ]] || [ "$WORK_DURATION" -le 0 ] || [ "$BREAK_DURATION" -le 0 ]; then
  echo "Error: Work and break durations must be positive integers greater than zero."
  exit 1
fi

# Create the log directory if it doesn't exist
if ! mkdir -p "$LOG_DIR"; then
  echo "Failed to create log directory $LOG_DIR" >&2
  exit 1
fi

# Check if the log file is empty to add a "START_OF_DAY" marker
if [ ! -s "$LOG_FILE" ] || ! grep -q "START_OF_DAY" "$LOG_FILE"; then
  debug_log "Log file is empty or 'START_OF_DAY' marker not found. Adding marker."
  echo "START_OF_DAY" > "$LOG_FILE"
else
  debug_log "Log file already exists and contains data."
fi

# Check for notification tools
NOTIFICATION_SUPPORT=true
if ! command -v terminal-notifier &>/dev/null && ! command -v notify-send &>/dev/null; then
  echo "Warning: Neither terminal-notifier nor notify-send are installed. Notifications will not be sent."
  NOTIFICATION_SUPPORT=false
fi
debug_log "Notification support: $NOTIFICATION_SUPPORT"

# Function to send notifications
send_notification() {
  local title=$1
  local message=$2
  debug_log "Sending notification: $title - $message"
  if $NOTIFICATION_SUPPORT; then
    if command -v terminal-notifier &>/dev/null; then
      terminal-notifier -title "$title" -message "$message"
    elif command -v notify-send &>/dev/null; then
      notify-send "$title" "$message"
    fi
  else
    echo "Notification: $title - $message"
  fi
}

# Function to display time in hh:mm:ss format
display_time() {
  local T=$1
  printf "%02d:%02d:%02d" $((T/3600)) $((T/60%60)) $((T%60))
}

# Function to log data to a file
log_to_file() {
  local timestamp=$(date +"%H:%M:%S")  # Time only, no date
  local task_name=$1
  local action=$2
  local duration=$3
  local formatted_duration=$(display_time "$duration")
  debug_log "Logging: $timestamp, $task_name, $action, $formatted_duration"
  echo "$timestamp, $task_name, $action, $formatted_duration" >> "$LOG_FILE"
}

# Initialize global variables
elapsed=0     # Time elapsed in the current period
is_work=1     # Flag for work (1) or break (0)

# Main timer for work and breaks
timer() {
  local duration=$1   # Duration of the period in seconds
  is_work=$2          # Flag for work or break
  elapsed=0           # Reset elapsed time counter
  local remaining=$duration  # Remaining time counter

  debug_log "Timer started for $duration seconds (is_work=$is_work)"

  if [ $is_work -eq 1 ]; then
    log_to_file "$TASK_NAME" "START_WORK" "$elapsed"
  else
    log_to_file "$TASK_NAME" "START_BREAK" "$elapsed"
  fi

  while [ $elapsed -lt $duration ]; do
    if [ $is_work -eq 1 ]; then
      printf "\r${BLUE}$TASK_NAME${RESET} - Elapsed time: ${GREEN}%s${RESET}, until break: ${RED}%s${RESET}" \
        "$(display_time $elapsed)" "$(display_time $remaining)"
    else
      printf "\r${CYAN}Break${RESET} - Time remaining: ${RED}%s${RESET}" "$(display_time $remaining)"
    fi
    sleep 1
    ((elapsed++))
    ((remaining--))
  done
  echo # Add a new line after the timer finishes

  # Log the end of the period
  if [ $is_work -eq 1 ]; then
    log_to_file "$TASK_NAME" "END_WORK" "$elapsed"
  else
    log_to_file "$TASK_NAME" "END_BREAK" "$elapsed"
  fi
}

# Function to calculate total work and break times for the day by task
calculate_total_times() {
  debug_log "Calculating total work and break times for the day by task"

  awk -F, -v yellow="$YELLOW" -v green="$GREEN" -v red="$RED" -v cyan="$CYAN" -v reset="$RESET" '
  {
    # Trim extra spaces from fields
    for (i = 1; i <= NF; i++) {
      gsub(/^ +| +$/, "", $i)
    }
    task = $2
    action = $3
    duration_str = $4
    split(duration_str, time_parts, ":")
    duration_seconds = time_parts[1] * 3600 + time_parts[2] * 60 + time_parts[3]
    if (action == "END_WORK") {
      work_time[task] += duration_seconds
    } else if (action == "END_BREAK") {
      break_time[task] += duration_seconds
    }
  }
  END {
    for (task in work_time) {
      total_work_seconds = work_time[task]
      total_break_seconds = break_time[task]
      work_hours = int(total_work_seconds / 3600)
      work_minutes = int((total_work_seconds % 3600) / 60)
      work_seconds = total_work_seconds % 60
      break_hours = int(total_break_seconds / 3600)
      break_minutes = int((total_break_seconds % 3600) / 60)
      break_seconds = total_break_seconds % 60

      # Colorful output for statistics
      printf("%sTask: %s%s\n", yellow, reset, cyan task, reset)
      printf("%s  Total work time:\t\t %02d:%02d:%02d%s\n", green, work_hours, work_minutes, work_seconds, reset)
      printf("%s  Total break time:\t\t %02d:%02d:%02d%s\n", red, break_hours, break_minutes, break_seconds, reset)
    }
  }
  ' "$LOG_FILE"
}

# Catch signals for exiting and display total statistics
trap 'echo -e "\nExiting timer..."; if [ $is_work -eq 1 ]; then log_to_file "$TASK_NAME" "END_WORK" "$elapsed"; else log_to_file "$TASK_NAME" "END_BREAK" "$elapsed"; fi; calculate_total_times; exit' SIGINT SIGTERM

# Main loop: work -> break
while true; do
  # Work period
  work_seconds=$((WORK_DURATION * 60))
  debug_log "Work period started: $work_seconds seconds"
  timer $work_seconds 1

  send_notification "Break" "Break started for task: $TASK_NAME. Break duration: $BREAK_DURATION minutes."

  # Break period
  break_seconds=$((BREAK_DURATION * 60))
  debug_log "Break period started: $break_seconds seconds"
  timer $break_seconds 0

  # Notification to return to work
  send_notification "End of Break" "Break ended. Return to task: $TASK_NAME."
done
