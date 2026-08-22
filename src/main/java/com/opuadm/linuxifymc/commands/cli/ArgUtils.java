package com.opuadm.linuxifymc.commands.cli;

import java.util.Arrays;

public final class ArgUtils {
    private ArgUtils() {
    }

    public static String[] argsAfterCommand(String[] args) {
        if (args == null || args.length <= 1) return new String[0];
        return Arrays.copyOfRange(args, 1, args.length);
    }

    public static String getPositional(String[] args, int n) {
        int count;
        int i;
        String argument;

        if (args == null || n <= 0) return null;
        count = 0;
        for (i = 1; i < args.length; i++) {
            argument = args[i];
            if (!argument.startsWith("-")) {
                count++;
                if (count == n) return argument;
            }
        }
        return null;
    }

    public static boolean hasFlag(String[] args, String flag) {
        int i;

        if (args == null || flag == null) return false;
        for (i = 1; i < args.length; i++) if (flag.equals(args[i])) return true;
        return false;
    }

    public static String joinAllArgs(String[] args) {
        String[] commandArguments;

        commandArguments = argsAfterCommand(args);
        return String.join(" ", commandArguments);
    }
}
