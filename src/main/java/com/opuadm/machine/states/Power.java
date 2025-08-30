package com.opuadm.machine.states;

import java.io.Serializable;

public class Power {
    private boolean isOn = false;
    private boolean isOff = true;
    private boolean isBooting = false;

    public int checkPowerStatus() {
        if (isOn && !isOff && !isBooting) {
            return 1;
        } else if (isOff && !isOn && !isBooting) {
            return 0;
        } else if (isBooting && !isOn && !isOff) {
            return 2; // Status 2 = Booting
        } else {
            return -1;
        }
    }

    public Serializable TurnOn() {
        if (isOn) {
            return "E: The machine is already on!";
        } else if (isBooting) {
            return "E: The machine is currently booting!";
        } else {
            isOff = false;
            isBooting = true;
        }
        return 0;
    }

    public Serializable TurnOff() {
        if (isOff) {
            return "E: The machine is already off!";
        } else if (isBooting) {
            return "E: The machine is currently booting!";
        } else {
            isOn = false;
        }
        return 0;
    }
}
