package com.opuadm.linuxifymc.machine.states;

import com.opuadm.linuxifymc.machine.clock.Timer;
import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.MessageFormat;

public class Boot {
    public static void Init(Player plr) {
        String msg1 = MessageFormat.format("[    {0}] LinuxifyMC Kernel version {1} {2}@{3}", Timer.getStamp(), LinuxifyMC.kernelver, plr.getName(), LinuxifyMC.hostname);
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg1);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg1);
        }

        String msg2 = MessageFormat.format("[    {0}] Command line: BOOT_IMAGE=/boot/vmlinuz-{1} root=/dev/sda1 ro quiet", Timer.getStamp(), LinuxifyMC.kernelver);
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg2);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg2);
        }

        String msg3 = MessageFormat.format("[    {0}] KERNEL supported cpus:", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg3);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg3);
        }

        String supportedCPU = MessageFormat.format("[    {0}]   x86_64", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, supportedCPU);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(supportedCPU);
        }

        String msg4 = MessageFormat.format("[    {0}] x86/fpu: Supporting XSAVE feature 0x001: 'x87 floating point registers'", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg4);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg4);
        }

        String msg5 = MessageFormat.format("[    {0}] x86/fpu: Supporting XSAVE feature 0x002: 'SSE registers'", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg5);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg5);
        }

        String msg6 = MessageFormat.format("[    {0}] x86/fpu: Supporting XSAVE feature 0x004: 'AVX registers'", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg6);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg6);
        }

        String msg7 = MessageFormat.format("[    {0}] NX (Execute Disable) protection: active", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg7);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg7);
        }

        String msg8 = MessageFormat.format("[    {0}] SMBIOS present.", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg8);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg8);
        }

        String msg9 = MessageFormat.format("[    {0}] EFI detected.", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg9);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg9);
        }

        String msg10 = MessageFormat.format("[    {0}] CPU: Generic", Timer.getStamp());
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg10);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg10);
        }

        try {
            Power.getFor(plr.getUniqueId()).ChangeStateVar(1);
        } catch (NoSuchMethodError | Exception e) {
            String errMsg = MessageFormat.format("[    {0}] Failed to power on virtual machine: {1}", Timer.getStamp(), e.getMessage());
            CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, errMsg);
            if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
                plr.sendMessage(errMsg);
            }
        }

        LinuxifyMC plugin = JavaPlugin.getPlugin(LinuxifyMC.class);
        plugin.getLoginPrompt().prompt(plr);
    }
}