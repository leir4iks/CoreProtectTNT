package com.leir4iks.coreprotecttnt.config;

import java.util.HashMap;
import java.util.Map;

public class Config {
    public GeneralSettings general = new GeneralSettings();
    public Formatting formatting = new Formatting();
    public Modules modules = new Modules();
    public Localization localization = new Localization();

    public static class GeneralSettings {
        public boolean debug = false;
        public UpdateCheckerSettings updateChecker = new UpdateCheckerSettings();
    }

    public static class UpdateCheckerSettings {
        public boolean enabled = true;
        public boolean autoDownload = true;
    }

    public static class Formatting {
        public String logPrefix = "#";
        public String causeSeparator = "-";
    }

    public static class Modules {
        public ModuleSettings blockExplosions = new ModuleSettings();
        public ModuleSettings entityExplosions = new ModuleSettings();
        public ModuleSettings fire = new ModuleSettings();
        public FrameSettings itemFrames = new FrameSettings();
        public ModuleSettings hanging = new ModuleSettings();
    }

    public static class ModuleSettings {
        public boolean enabled = true;
        public boolean disableUnknown = false;
    }

    public static class FrameSettings extends ModuleSettings {
        public boolean logPlacement = true;
        public boolean logRotation = true;
        public boolean logContentChange = true;
        public boolean logPistonDestruction = true;
        public boolean logWaterDestruction = true;
        public boolean logPhysicsDestruction = true;
    }

    public static class Localization {
        public Messages messages = new Messages();
        public Map<String, String> causes = new HashMap<>();
    }

    public static class Messages {
        public String prefix = "&6[CoreProtectTNT] &e";
        public String updateChecking = "Checking for updates...";
        public String updateAvailable = "A new version is available!";
        public String updateDownloaded = "&aUpdate downloaded. Restart to apply.";
        public String updateFailed = "&cUpdate check failed.";
        public String noPermission = "&cYou do not have permission.";
        public String unknownSourceAlert = "&cAction blocked: Unknown source.";
    }
}