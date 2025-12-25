package com.opuadm.linuxifymc.machine.states;

import com.opuadm.linuxifymc.machine.clock.Timer;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.MessageFormat;
import java.util.UUID;

public class Boot {
    public static void Init(Player plr) {
        UUID uuid = plr.getUniqueId();
        String msg1 = MessageFormat.format("[    {0}] LinuxifyMC Kernel version {1} {2}@{3}", Timer.getStamp(uuid), LinuxifyMC.kernelver, plr.getName(), LinuxifyMC.hostname);
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg1);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg1);
        }

        String msg2 = MessageFormat.format("[    {0}] Command line: BOOT_IMAGE=/boot/vmlinuz-{1} root=/dev/sda1 ro quiet", Timer.getStamp(uuid), LinuxifyMC.kernelver);
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg2);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg2);
        }

        String msg3 = MessageFormat.format("[    {0}] KERNEL supported cpus:", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg3);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg3);
        }

        String msg4 = MessageFormat.format("[    {0}]   Generic x86_64", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg4);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg4);
        }

        String msg5 = MessageFormat.format("[    {0}] SMBIOS present.", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg5);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg5);
        }

        String msg6 = MessageFormat.format("[    {0}] EFI detected.", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg6);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg6);
        }

        String msg7 = MessageFormat.format("[    {0}] CPU: Generic", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg7);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg7);
        }

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            CustomLogger.BootLog(plr.getPlayer(), Levels.ERROR, "Boot interrupted: " + e.getMessage());
        }

        String msg8 = MessageFormat.format("[    {0}] Checking if player is new...", Timer.getStamp(uuid));
        CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg8);
        if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
            plr.sendMessage(msg8);
        }

        Object exists = FakeFS.DB == null ? null : FakeFS.DB.singleValueQuery("SELECT 1 FROM fs_saves WHERE player_uuid = ?", uuid.toString());
        boolean isNew = (exists == null);
        FakeFS plrFS = FakeFS.getPlayerFS(uuid, plr.getName());

        if (plrFS != null) {
            String msg9 = MessageFormat.format("[    {0}] Loading filesystem...", Timer.getStamp(uuid));
            CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg9);
            if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
                plr.sendMessage(msg9);
            }

            plrFS.loadFS(uuid);

            if (isNew) {
                String msg10 = MessageFormat.format("[    {0}] You are new here, so setting up system files...", Timer.getStamp(uuid));
                CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, msg10);
                if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
                    plr.sendMessage(msg10);
                }
                plrFS.setupSysFiles();
            }

            plrFS.upgradeFS(plrFS);
        }

        try {
            Power.getFor(plr.getUniqueId()).ChangeStateVar(1);
        } catch (NoSuchMethodError | Exception e) {
            String errMsg = MessageFormat.format("[    {0}] Failed to power on virtual machine: {1}", Timer.getStamp(uuid), e.getMessage());
            CustomLogger.BootLog(plr.getPlayer(), Levels.GENERAL, errMsg);
            if (plr.hasPermission("linuxifymc.command.bootlogs.sendinchat")) {
                plr.sendMessage(errMsg);
            }
        }

        LinuxifyMC plugin = JavaPlugin.getPlugin(LinuxifyMC.class);
        plugin.getLoginPrompt().prompt(plr);
    }
}