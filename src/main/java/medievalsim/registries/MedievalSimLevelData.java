package medievalsim.registries;
import medievalsim.util.ModLogger;
import medievalsim.zones.AdminZonesLevelData;
import necesse.engine.registries.LevelDataRegistry;

public class MedievalSimLevelData {
    public static void registerCore() {
        LevelDataRegistry.registerLevelData((String)"adminzonesdata", AdminZonesLevelData.class);
        ModLogger.debug("Registered LevelData classes");
    }
}

