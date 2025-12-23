package medievalsim.registries;

import medievalsim.guilds.GuildManager;
import necesse.engine.registries.WorldDataRegistry;
import medievalsim.util.ModLogger;

public class MedievalSimWorldData {
    public static void registerCore() {
        // Register GuildManager with key matching GuildManager.DATA_KEY
        WorldDataRegistry.registerWorldData(GuildManager.DATA_KEY, GuildManager.class);
        ModLogger.info("Registered WorldData: %s", GuildManager.DATA_KEY);
    }
}
