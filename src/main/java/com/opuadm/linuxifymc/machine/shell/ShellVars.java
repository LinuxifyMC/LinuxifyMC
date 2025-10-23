package com.opuadm.linuxifymc.machine.shell;

import java.util.List;

public class ShellVars
{
    public static final String[] cmds = {
            "help",
            "test",
            "uname",
            "cd",
            "ls",
            "chmod",
            "chown",
            "mkdir",
            "rm",
            "cat",
            "serverfetch",
            "neofetch",
            "echo",
            "touch",
            "sudo"
    };

    public static List<String> LsOpts() {
        return List.of("-a", "-o", "-ao");
    }

    public static List<String> UnameOpts()  { return List.of("-s", "-v", "--help", "-h"); }

    public static List<String> UnameOptsS()  { return List.of("-v"); }

    public static List<String> MkdirOpts() { return List.of("--help", "-h"); }

    public static List<String> ChmodPerms() { return List.of("777", "644", "755", "700", "766"); }

    public static List<String> RMOpts() { return List.of("-r", "-f"); }
}
