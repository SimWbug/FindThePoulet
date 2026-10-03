package fr.simon.findthepoulet.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<gold>Poulet</gold>]</dark_gray> ";

    private Msg() {}

    public static Component mm(String mini) {
        return MM.deserialize(mini);
    }

    /** Texte d'item (sans italique). */
    public static Component item(String mini) {
        return MM.deserialize("<!italic>" + mini);
    }

    public static void send(Audience to, String mini) {
        to.sendMessage(MM.deserialize(PREFIX + mini));
    }

    public static void sound(Audience to, String key, float pitch) {
        to.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, pitch));
    }

    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
