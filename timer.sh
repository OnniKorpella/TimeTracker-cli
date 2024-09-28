#!/bin/bash

# ---------------17 20 lunch ------------------------
DEBUG_MODE=false  # Переключатель для режима отладки

# Функция для вывода отладочной информации
debug_log() {
  if [ "$DEBUG_MODE" = true ]; then
    echo "[DEBUG] $1"
  fi
}

# Проверяем наличие необходимых аргументов
if [ "$#" -ne 3 ]; then
  echo "Использование: $0 \"Название задачи\" <время работы в минутах> <время перерыва в минутах>"
  exit 1
fi

# Переменные
TASK_NAME=$1
WORK_DURATION=$2   # Время работы в минутах
BREAK_DURATION=$3  # Время перерыва в минутах
CURRENT_DATE=$(date +"%Y-%m-%d")  # Текущая дата в формате YYYY-MM-DD
LOG_DIR="./logs"  # Директория для логов
LOG_FILE="$LOG_DIR/log_${CURRENT_DATE}.csv"  # Имя файла для записи логов с датой в формате CSV

# Отладочные сообщения
debug_log "Запуск задачи: $TASK_NAME"
debug_log "Время работы: $WORK_DURATION минут"
debug_log "Время перерыва: $BREAK_DURATION минут"
debug_log "Файл лога: $LOG_FILE"

# Цвета
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
  RESET="\033[0m"  # Сброс цвета
else
  GREEN=""
  RED=""
  YELLOW=""
  BLUE=""
  CYAN=""
  RESET=""
fi

# Проверка, что введены положительные числа
if ! [[ "$WORK_DURATION" =~ ^[0-9]+$ ]] || ! [[ "$BREAK_DURATION" =~ ^[0-9]+$ ]] || [ "$WORK_DURATION" -le 0 ] || [ "$BREAK_DURATION" -le 0 ]; then
  echo "Ошибка: Время работы и перерыва должны быть положительными целыми числами больше нуля."
  exit 1
fi

# Создаём директорию для логов, если её нет
if ! mkdir -p "$LOG_DIR"; then
  echo "Не удалось создать директорию логов $LOG_DIR" >&2
  exit 1
fi

# Проверка, является ли файл пустым, для добавления метки начала дня
if [ ! -s "$LOG_FILE" ] || ! grep -q "START_OF_DAY" "$LOG_FILE"; then
  debug_log "Файл лога пустой или метка начала дня не найдена. Добавляем метку начала дня."
  echo "START_OF_DAY" > "$LOG_FILE"
else
  debug_log "Файл лога уже существует и содержит данные."
fi

# Проверка наличия инструментов для уведомлений
NOTIFICATION_SUPPORT=true
if ! command -v terminal-notifier &>/dev/null && ! command -v notify-send &>/dev/null; then
  echo "Внимание: ни terminal-notifier, ни notify-send не установлены, уведомления не будут отправляться."
  NOTIFICATION_SUPPORT=false
fi
debug_log "Поддержка уведомлений: $NOTIFICATION_SUPPORT"

# Функция для отправки уведомлений
send_notification() {
  local title=$1
  local message=$2
  debug_log "Отправка уведомления: $title - $message"
  if $NOTIFICATION_SUPPORT; then
    if command -v terminal-notifier &>/dev/null; then
      terminal-notifier -title "$title" -message "$message"
    elif command -v notify-send &>/dev/null; then
      notify-send "$title" "$message"
    fi
  else
    echo "Уведомление: $title - $message"
  fi
}

# Функция для отображения времени в формате hh:mm:ss
display_time() {
  local T=$1
  printf "%02d:%02d:%02d" $((T/3600)) $((T/60%60)) $((T%60))
}

# Функция для записи в лог-файл
log_to_file() {
  local timestamp=$(date +"%H:%M:%S")  # Только время, без даты
  local task_name=$1
  local action=$2
  local duration=$3
  local formatted_duration=$(display_time "$duration")
  debug_log "Запись в лог: $timestamp, $task_name, $action, $formatted_duration"
  echo "$timestamp, $task_name, $action, $formatted_duration" >> "$LOG_FILE"
}

# Инициализация глобальных переменных
elapsed=0     # Счётчик времени, прошедшего с начала периода
is_work=1     # Флаг, является ли это работа (1) или перерыв (0)

# Основной таймер для работы и перерыва
timer() {
  local duration=$1   # продолжительность периода в секундах
  is_work=$2          # флаг, является ли это работа или перерыв
  elapsed=0           # Сброс счётчика времени
  local remaining=$duration  # Счётчик оставшегося времени

  debug_log "Таймер запущен на $duration секунд (is_work=$is_work)"

  if [ $is_work -eq 1 ]; then
    log_to_file "$TASK_NAME" "START_WORK" "$elapsed"
  else
    log_to_file "$TASK_NAME" "START_BREAK" "$elapsed"
  fi

  while [ $elapsed -lt $duration ]; do
    if [ $is_work -eq 1 ]; then
      printf "\r${BLUE}$TASK_NAME${RESET} - Затрачено времени: ${GREEN}%s${RESET}, до перерыва: ${RED}%s${RESET}" \
        "$(display_time $elapsed)" "$(display_time $remaining)"
    else
      printf "\r${CYAN}Перерыв${RESET} - До конца перерыва: ${RED}%s${RESET}" "$(display_time $remaining)"
    fi
    sleep 1
    ((elapsed++))
    ((remaining--))
  done
  echo # Добавляем новую строку после завершения таймера

  # Записываем завершение периода
  if [ $is_work -eq 1 ]; then
    log_to_file "$TASK_NAME" "END_WORK" "$elapsed"
  else
    log_to_file "$TASK_NAME" "END_BREAK" "$elapsed"
  fi
}

# Функция для подсчёта общего времени работы и перерывов за день по каждой задаче
calculate_total_times() {
  debug_log "Подсчёт общего времени работы и перерывов за день по каждой задаче"

  awk -F, -v yellow="$YELLOW" -v green="$GREEN" -v red="$RED" -v cyan="$CYAN" -v reset="$RESET" '
  {
    # Удаляем лишние пробелы в полях
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

      # Цветной вывод статистики
      printf("%sЗадача: %s%s\n", yellow,reset, cyan task, reset)
      printf("%s  Общее время работы:\t\t %02d:%02d:%02d%s\n", green, work_hours, work_minutes, work_seconds, reset)
      printf("%s  Общее время перерывов:\t %02d:%02d:%02d%s\n", red, break_hours, break_minutes, break_seconds, reset)
    }
  }
  ' "$LOG_FILE"
}


# Ловим сигнал завершения и выводим общую статистику
trap 'echo -e "\nЗавершение таймера..."; if [ $is_work -eq 1 ]; then log_to_file "$TASK_NAME" "END_WORK" "$elapsed"; else log_to_file "$TASK_NAME" "END_BREAK" "$elapsed"; fi; calculate_total_times; exit' SIGINT SIGTERM

# Основной цикл: работа -> перерыв
while true; do
  # Рабочий период
  work_seconds=$((WORK_DURATION * 60))
  debug_log "Рабочий период начат: $work_seconds секунд"
  timer $work_seconds 1

  send_notification "Перерыв" "Начался перерыв по задаче: $TASK_NAME. Время перерыва: $BREAK_DURATION минут."

  # Период перерыва
  break_seconds=$((BREAK_DURATION * 60))
  debug_log "Период перерыва начат: $break_seconds секунд"
  timer $break_seconds 0

  # Уведомление о возврате к работе
  send_notification "Конец перерыва" "Перерыв завершён. Возвращайтесь к задаче: $TASK_NAME."
done
