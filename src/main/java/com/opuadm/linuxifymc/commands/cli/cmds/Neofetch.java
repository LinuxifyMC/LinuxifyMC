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
                "  ####*==##=***%%==+*#%%%%@ ",
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

        int chatWidthPixels = 320;
        int uniformCharWidth = 6;
        int defaultCharWidth = 6;
        int artWidth = 28;

        boolean isPlayer = sender instanceof Player;
        Key uniform = Key.key("minecraft", "uniform");

        for (int i = 0; i < art.length; i++) {
            String artLine = art[i];
            String infoLine = i < info.length ? info[i] : "";

            if (isPlayer) {
                int artPixels = artWidth * uniformCharWidth;
                int infoPixels = infoLine.length() * defaultCharWidth;
                int usedPixels = artPixels + infoPixels;
                int remainingPixels = chatWidthPixels - usedPixels;
                int paddingChars = Math.max(0, remainingPixels / uniformCharWidth);

                Component artComp = Component.text(artLine).font(uniform);
                Component spacerComp = Component.text(" ".repeat(paddingChars)).font(uniform);
                Component infoComp = Component.text(infoLine);

                player.sendMessage(artComp.append(spacerComp).append(infoComp));
            } else {
                sender.sendMessage(artLine + " ".repeat(Math.max(0, 53 - artWidth - infoLine.length())) + infoLine);
            }
        }

        return true;
    }
}
