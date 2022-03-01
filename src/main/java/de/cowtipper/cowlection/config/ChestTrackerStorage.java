package de.cowtipper.cowlection.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class ChestTrackerStorage {
    public static String stringChestCache;
    public static String stringDoubleChestCache;
    private Property propChestCache;
    private Property propDoubleChestCache;
    private final Configuration cfg;

    public ChestTrackerStorage(Configuration configuration) {
        cfg = configuration;
        initConfig();
    }

    private void initConfig() {
        cfg.load();
        propChestCache = cfg.get(Configuration.CATEGORY_CLIENT,
                        "chestCache", "", "Location of chests & items within")
                .setShowInGui(false);
        propDoubleChestCache = cfg.get(Configuration.CATEGORY_CLIENT,
                        "doubleChestCache", "", "Location of double chests & their directions")
                .setShowInGui(false);
        stringChestCache = propChestCache.getString();
        stringDoubleChestCache = propDoubleChestCache.getString();

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public void setChestCache(String stringChestCache) {
        ChestTrackerStorage.stringChestCache = stringChestCache;
        propChestCache.set(stringChestCache);
        cfg.save();
    }

    public void setDoubleChestCache(String stringDoubleChestCache) {
        ChestTrackerStorage.stringDoubleChestCache = stringDoubleChestCache;
        propDoubleChestCache.set(stringDoubleChestCache);
        cfg.save();
    }
}
