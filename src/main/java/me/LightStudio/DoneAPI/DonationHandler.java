package me.LightStudio.DoneAPI;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class DonationHandler {
    private final JavaPlugin plugin;

    public DonationHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleDonation(String streamerTag, String donorName, int amount, String message) {
        List<String> commands = getCommandsForAmount(amount);
        if (commands == null || commands.isEmpty()) {
            return; // 설정된 보상이 없으면 무시
        }

        for (String command : commands) {
            String formattedCommand = command
                    .replace("%tag%", streamerTag)
                    .replace("%name%", donorName)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%message%", message);

            sendCommandToVelocity(formattedCommand);
        }
    }

    private List<String> getCommandsForAmount(int amount) {
        return DoneConnector.donationRewards.getOrDefault(amount, DoneConnector.donationRewards.get(0));
    }

    private void sendCommandToVelocity(String command) {
        byte[] message = command.getBytes(StandardCharsets.UTF_8);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPluginMessage(plugin, "doneconnector:donation", message);
            break; // 한 명한테만 보내면 됨
        }
    }
}
