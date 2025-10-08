package com.opuadm.linuxifymc.machine.login;

import org.bukkit.entity.Player;

public class Login {
    private final Player player;
    private final String[] users;

    public Login(Player player) {
        this.player = player;
        if (player != null) {
            this.users = new String[] { player.getName(), "root" };
        } else {
            this.users = new String[] { "root" };
        }
    }

    public Login(String[] users) {
        this.player = null;
        this.users = (users != null) ? users.clone() : new String[] { "root" };
    }

    public void login() {

    }

    public String getPlayerName() {
        return (player != null) ? player.getName() : null;
    }

    public String[] getUsers() {
        return users.clone();
    }
}
