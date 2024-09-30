package src;


import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SoundUtil {
    public void playSound(String soundFilePath) {
        try {
            // Загружаем ресурс из папки ресурсов
            URL soundURL = getClass().getClassLoader().getResource(soundFilePath);
            if (soundURL == null) {
                System.err.println("Файл звука не найден в ресурсах: " + soundFilePath);
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();

            clip.open(audioStream);
            clip.start();
        } catch (UnsupportedAudioFileException e) {
            System.err.println("Неподдерживаемый формат аудио файла: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода при воспроизведении звука: " + e.getMessage());
        } catch (LineUnavailableException e) {
            System.err.println("Аудио линия недоступна: " + e.getMessage());
        }
    }
}
