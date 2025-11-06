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
                "           +++==           ",
                "        +====++++++=        ",
                "    ++++++++#@@#*++++++==   ",
                "  **+++++++##%#%@+++++++*## ",
                "  *#****++=*=--*@+==+##%%%@ ",
                "  ##%#%##**#-=::#@%%##%%%%% ",
                "  *####%#%#.....-%@@%%%%%%% ",
                "  ######%#:......:@@@%%%%%% ",
                "  ####%%*+:......:@@%%%%%%@ ",
                "  ####+---+%....--=--*%%%%% ",
                "  ####*==--=***%%==+*#%%%%@ ",
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
        int artColumnWidth = 0;
        for (String s : art) if (s.length() > artColumnWidth) artColumnWidth = s.length();
        int infoMaxLen = 0;
        for (String s : info) if (s.length() > infoMaxLen) infoMaxLen = s.length();
        int fixedTotalWidth = 80;
        if (fixedTotalWidth < infoMaxLen) fixedTotalWidth = infoMaxLen + 2;
        boolean isPlayer = sender instanceof Player;
        Key uniform = Key.key("minecraft", "uniform");
        for (int i = 0; i < art.length; i++) {
            String artLine = art[i];
            if (artLine.length() < artColumnWidth) {
                StringBuilder sb = new StringBuilder(artLine);
                while (sb.length() < artColumnWidth) sb.append(' ');
                artLine = sb.toString();
            } else if (artLine.length() > artColumnWidth) {
                artLine = artLine.substring(0, artColumnWidth);
            }
            String infoLine = i < info.length ? info[i] : "";
            int spacesNeeded = fixedTotalWidth - infoLine.length();
            if (spacesNeeded < artColumnWidth + 1) spacesNeeded = artColumnWidth + 1;
            StringBuilder spacer = new StringBuilder();
            spacer.append(" ".repeat(Math.max(0, spacesNeeded - artLine.length())));
            if (isPlayer) {
                Component artComp = Component.text(artLine).font(uniform);
                Component infoComp = Component.text(spacer + infoLine);
                player.sendMessage(artComp.append(infoComp));
            } else {
                sender.sendMessage(artLine + spacer + infoLine);
            }
        }
        return true;
    }
}