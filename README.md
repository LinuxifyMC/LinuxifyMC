# LinuxifyMC
LinuxifyMC is a Minecraft plugin that allows you to execute simulated linux commands in Minecraft.
A simulated file system is included with a database.

There are some commands that Linux doesn't have. But if you want to disable these for yourself, just run `/linuxifymc user set non-linux-commands false`.

All commands (such as help, ls, cd, etc.) can be executed using `/cli <command>`. Please note that executing `/cli` itself will just do an empty output, so use `/cli help` instead.
If you wonder why you cannot execute commands using `/cli <command>` directly like in LinuxifyMC v0.1.0, it's because of the Boot change (introduced in LinuxifyMC v0.1.1) which doesn't let you execute commands unless the virtual computer is booted (use `/vcomputer start` or it's aliases with start argument to start the virtual computer)

# Notes
This plugin does not mimic the Linux terminal perfectly, but it does give you an experience of using a linux terminal in Minecraft. If you want to make this plugin more realistic, try contributing atleast.

This plugin has currently been tested on Paper 1.21.4. Starting from LinuxifyMC 0.1.1.1, the native version will be Paper 1.21.8 (instead of 1.21.4).

If you want to know what things are planned to be done, check out TODO.md. If you want to know about how you can contribute to this project correctly, visit CONTRIBUTING.md.

![bStats](https://bstats.org/signatures/bukkit/LinuxifyMC.svg)