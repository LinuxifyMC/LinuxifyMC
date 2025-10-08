// Levels for logging (in the custom logging system)
package com.opuadm.linuxifymc.machine.logs;

public enum Levels {
    // Log Levels (Higher number = more severe)
    GENERAL("General", 0),
    DEBUG("Debug", 0),
    INFO("Info", 0),
    WARNING("Warning", 1),
    ERROR("Error", 2),
    CRITICAL("Critical", 3),
    FATAL("Fatal", 4);

    private final String levelName;

    Levels(String levelName, int severity) {
        this.levelName = levelName;
    }

    @Override
    public String toString() {
        return levelName;
    }
}