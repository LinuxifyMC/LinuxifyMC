package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;

@SuppressWarnings("unused")
public class Neofetch {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String[] art = new String[] {
                "           +++==            ",
                "        +====++++++=        ",
                "    ++++++++#@@#*++++++==   ",
                "  **+++++++##%#%@+++++++*## ",
                "  *#****++=*=##*@+==+##%%%@ ",
                "  ##%#%##**#====#@%%##%%%%% ",
                "  *####%#%#~~~~~=%@@%%%%%%% ",
                "  ######%#=~~~~~~=@@@%%%%%% ",
                "  ####%%*+=~~~~~~=@@%%%%%%@ ",
                "  ####+####+%~~~~=##=*%%%%% ",
                "  ####*==##=***%%==+*#%%%%% ",
                "    ##########%%%%%%%%%%    ",
                "         #####%%%%%%        ",
                "            #%%%            "
        };

        String[] info = new String[] {
                "LinuxifyMC " + LinuxifyMC.version,
                LinuxifyMC.kernelname + " " + LinuxifyMC.kernelver,
                LinuxifyMC.shellname + " " + LinuxifyMC.shellver + " (Minecraft)",
                "Current Player: " + player.getName()
        };

        int longestInfo = 0;
        for (String line : info) {
            longestInfo = Math.max(longestInfo, line.length());
        }

        int CHAT_WIDTH = 75;

        int INFO_START = CHAT_WIDTH - longestInfo;

        boolean isPlayer = sender instanceof Player;
        Key uniform = Key.key("minecraft", "uniform");

        for (int i = 0; i < art.length; i++) {
            String artLine = art[i];
            String infoLine = i < info.length ? info[i] : "";

            String paddedInfo = String.format("%-" + longestInfo + "s", infoLine);

            if (isPlayer) {
                StringBuilder fullLine = new StringBuilder();
                fullLine.append(artLine);

                int currentPos = artLine.length();
                int spacesNeeded = INFO_START - currentPos;

                fullLine.append(" ".repeat(Math.max(0, spacesNeeded)));

                fullLine.append(paddedInfo);

                player.sendMessage(Component.text(fullLine.toString()).font(uniform));
            } else {
                int currentPos = artLine.length();
                int spacesNeeded = INFO_START - currentPos;
                sender.sendMessage(artLine + " ".repeat(Math.max(0, spacesNeeded)) + paddedInfo);
            }
        }
        return true;
    }
}
