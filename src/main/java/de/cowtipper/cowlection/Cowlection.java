package de.cowtipper.cowlection;

import de.cowtipper.cowlection.chesttracker.ChestTracker;
import de.cowtipper.cowlection.command.*;
import de.cowtipper.cowlection.config.ChestTrackerStorage;
import de.cowtipper.cowlection.config.CredentialStorage;
import de.cowtipper.cowlection.config.MooConfig;
import de.cowtipper.cowlection.handler.DungeonCache;
import de.cowtipper.cowlection.handler.FriendsHandler;
import de.cowtipper.cowlection.handler.PlayerCache;
import de.cowtipper.cowlection.listener.ChatListener;
import de.cowtipper.cowlection.listener.PlayerListener;
import de.cowtipper.cowlection.partyfinder.Rules;
import de.cowtipper.cowlection.util.ChatHelper;
import de.cowtipper.cowlection.util.VersionChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;

@Mod(modid = Cowlection.MODID, name = Cowlection.MODNAME, version = Cowlection.VERSION,
        clientSideOnly = true,
        updateJSON = "https://raw.githubusercontent.com/cow-mc/Cowlection/master/update.json")
public class Cowlection {
    public static final String MODID = "@MODID@";
    public static final String VERSION = "@VERSION@";
    public static final String MODNAME = "@MODNAME@";
    public static final String GITURL = "@GITURL@";
    public static final String INVITE_URL = "https://discord.gg/fU2tFPf";
    public static KeyBinding[] keyBindings;
    private static Cowlection instance;
    private File modsDir;
    private File modOutDir;
    private MooConfig config;
    private CredentialStorage moo;
    private ChestTrackerStorage chestStorage;
    private Rules partyFinderRules;
    private FriendsHandler friendsHandler;
    private VersionChecker versionChecker;
    private ChatHelper chatHelper;
    private PlayerCache playerCache;
    private DungeonCache dungeonCache;
    private ChestTracker chestTracker;
    private Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        instance = this;
        logger = e.getModLog();
        modsDir = e.getSourceFile().getParentFile();

        chatHelper = new ChatHelper();

        File configDir = new File(e.getModConfigurationDirectory(), MODID + File.separatorChar);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        friendsHandler = new FriendsHandler(this, new File(configDir, "friends.json"));
        moo = new CredentialStorage(new Configuration(new File(configDir, "do-not-share-me-with-other-players.cfg")));
        chestStorage = new ChestTrackerStorage(new Configuration(new File(configDir, "chestStorage.cfg")));
        partyFinderRules = new Rules(this, new File(configDir, "partyfinder-rules.json"));
        config = new MooConfig(this, new Configuration(new File(configDir, MODID + ".cfg"), "2"));
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new ChatListener(this));
        MinecraftForge.EVENT_BUS.register(new PlayerListener(this));
        ClientCommandHandler.instance.registerCommand(new MooCommand(this));
        ClientCommandHandler.instance.registerCommand(new NumerousCommandsCommand());
        ClientCommandHandler.instance.registerCommand(new ReplyCommand());
        ClientCommandHandler.instance.registerCommand(new ShrugCommand(this));
        for (String tabCompletableNamesCommand : MooConfig.tabCompletableNamesCommands) {
            ClientCommandHandler.instance.registerCommand(new TabCompletableCommand(this, tabCompletableNamesCommand));
        }
        // key bindings
        keyBindings = new KeyBinding[2];
        keyBindings[0] = new KeyBinding("key.cowlection.moo", Keyboard.KEY_NONE, "key.cowlection.category");
        keyBindings[1] = new KeyBinding("key.cowlection.waila", Keyboard.KEY_NONE, "key.cowlection.category");

        for (KeyBinding keyBinding : keyBindings) {
            ClientRegistry.registerKeyBinding(keyBinding);
        }
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        versionChecker = new VersionChecker(this);
        playerCache = new PlayerCache();
        modOutDir = new File(Minecraft.getMinecraft().mcDataDir, Cowlection.MODID.toLowerCase() + "_out");
    }

    public MooConfig getConfig() {
        return config;
    }

    public ChestTrackerStorage getChestStorage() {
        return chestStorage;
    }

    public CredentialStorage getMoo() {
        return moo;
    }

    public Rules getPartyFinderRules() {
        return partyFinderRules;
    }

    public FriendsHandler getFriendsHandler() {
        return friendsHandler;
    }

    public VersionChecker getVersionChecker() {
        return versionChecker;
    }

    public ChatHelper getChatHelper() {
        return chatHelper;
    }

    public PlayerCache getPlayerCache() {
        return playerCache;
    }

    public DungeonCache getDungeonCache() {
        if (dungeonCache == null) {
            dungeonCache = new DungeonCache(this);
        }
        return dungeonCache;
    }

    public boolean enableChestTracker() {
        if (chestTracker == null) {
            chestTracker = new ChestTracker(this);
            return true;
        }
        return false;
    }

    public boolean disableChestTracker() {
        if (chestTracker != null) {
            chestTracker.clear();
            chestTracker = null;
            return true;
        }
        return false;
    }

    public ChestTracker getChestTracker() {
        return chestTracker;
    }

    public File getModsDirectory() {
        return modsDir;
    }

    public File getModOutDirectory() {
        if (!modOutDir.exists() && !modOutDir.mkdirs()) {
            // dir didn't exist and couldn't be created
            return null;
        }
        return modOutDir;
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Get mod's instance; instead of this method use dependency injection where possible
     */
    public static Cowlection getInstance() {
        return instance;
    }
}
