package com.opuadm.linuxifymc.commands.cli;

import java.util.Arrays;

public final class ArgUtils {
    private ArgUtils() {}

    public static String[] argsAfterCommand(String[] args) {
        if (args == null || args.length <= 1) return new String[0];
        return Arrays.copyOfRange(args, 1, args.length);
    }

    public static String getPositional(String[] args, int n) {
        if (args == null || n <= 0) return null;
        int count = 0;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("-")) {
                count++;
                if (count == n) return a;
            }
        }
        return null;
    }

    public static boolean hasFlag(String[] args, String flag) {
        if (args == null || flag == null) return false;
        for (int i = 1; i < args.length; i++) if (flag.equals(args[i])) return true;
        return false;
    }

    /* public static String getFlagValue(String[] args, String flag) {
        if (args == null || flag == null) return null;
        for (int i = 1; i < args.length - 1; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    public static String[] allPositionals(String[] args) {
        if (args == null || args.length <= 1) return new String[0];
        List<String> out = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("-")) out.add(args[i]);
        }
        return out.toArray(new String[0]);
    } */

    public static String joinAllArgs(String[] args) {
        String[] a = argsAfterCommand(args);
        return String.join(" ", a);
    }
}

