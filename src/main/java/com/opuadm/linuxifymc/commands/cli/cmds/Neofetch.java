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

        int chatWidthPixels = 320;
        int uniformCharWidth = 6;
        int totalChars = chatWidthPixels / uniformCharWidth;

        boolean isPlayer = sender instanceof Player;
        Key uniform = Key.key("minecraft", "uniform");

        for (int i = 0; i < art.length; i++) {
            String artLine = art[i];
            String infoLine = i < info.length ? info[i] : "";

            int artChars = artLine.length();
            int infoChars = infoLine.length();
            int infoStartPos = totalChars - infoChars;
            int spacerChars = infoStartPos - artChars;

            if (isPlayer) {
                Component artComp = Component.text(artLine).font(uniform);
                Component spacerComp = Component.text(" ".repeat(Math.max(0, spacerChars))).font(uniform);
                Component infoComp = Component.text(infoLine).font(uniform);
                player.sendMessage(artComp.append(spacerComp).append(infoComp));
            } else {
                int pad = Math.max(0, infoStartPos - artChars);
                sender.sendMessage(artLine + " ".repeat(pad) + infoLine);
            }
        }

        return true;
    }
}
