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
        if (fixedTotalWidth < infoMaxLen + 1) fixedTotalWidth = infoMaxLen + 1;

        boolean isPlayer = sender instanceof Player;
        Key uniform = Key.key("minecraft", "uniform");

        final String FIGURE_SPACE = "\u2007";

        for (int i = 0; i < art.length; i++) {
            String artLine = art[i];
            artLine = artLine.replaceAll("[\\r\\n]+$", "");

            String infoLine = i < info.length ? info[i] : "";

            int infoLen = infoLine.length();
            int infoStart = fixedTotalWidth - infoLen;

            if (infoStart < 1) infoStart = 1;

            String artForOutput = artLine;
            if (artForOutput.length() > infoStart - 1) {
                artForOutput = artForOutput.substring(0, Math.max(0, infoStart - 1));
            }

            int padCount = infoStart - artForOutput.length();
            if (padCount < 1) padCount = 1;

            if (isPlayer) {
                String spacer = FIGURE_SPACE.repeat(padCount);
                Component artComp = Component.text(artForOutput).font(uniform);
                Component infoComp = Component.text(spacer + infoLine);
                player.sendMessage(artComp.append(infoComp));
            } else {
                String spacer = " ".repeat(padCount);
                sender.sendMessage(artForOutput + spacer + infoLine);
            }
        }

        return true;
    }
}