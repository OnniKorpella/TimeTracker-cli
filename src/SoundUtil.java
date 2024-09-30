package src;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

class SoundUtil {
    public void playSound(String soundFilePath) {
        try {
            File soundFile = new File(soundFilePath);
            if (!soundFile.exists()) {
                System.err.println("Файл звука не найден: " + soundFilePath);
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();

            clip.open(audioStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Не удалось воспроизвести звук: " + e.getMessage());
        }
    }
}