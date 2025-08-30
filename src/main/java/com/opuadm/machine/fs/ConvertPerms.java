package com.opuadm.machine.fs;

public class ConvertPerms {
    public static String symbolicToOctal(String symbolic) {
        int[] perms = new int[3];
        for (int i = 0; i < 3; i++) {
            int val = 0;
            if (symbolic.charAt(i * 3 + 1) == 'r') perms[i] += 4;
            if (symbolic.charAt(i * 3 + 2) == 'w') perms[i] += 2;
            if (symbolic.charAt(i * 3 + 3) == 'x') perms[i] += 1;
            perms[i] = val;
        }
        return "" + perms[0] + perms[1] + perms[2];
    }

    public static String octalToSymbolic(String octal) {
        StringBuilder sb = new StringBuilder();
        for (char c : octal.toCharArray()) {
            int val = c - '0';
            sb.append((val & 4) != 0 ? 'r' : '-');
            sb.append((val & 2) != 0 ? 'w' : '-');
            sb.append((val & 1) != 0 ? 'x' : '-');
        }
        return sb.toString();
    }
}
