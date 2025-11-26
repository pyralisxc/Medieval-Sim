package medievalsim.registries;
import medievalsim.banking.BankingLevelData;
import medievalsim.util.ModLogger;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.SettlementProtectionLevelData;
import necesse.engine.registries.LevelDataRegistry;

public class MedievalSimLevelData {
    public static void registerCore() {
        LevelDataRegistry.registerLevelData((String)"adminzonesdata", AdminZonesLevelData.class);
        LevelDataRegistry.registerLevelData((String)"settlementprotectiondata", SettlementProtectionLevelData.class);
        LevelDataRegistry.registerLevelData((String)"bankingdata", BankingLevelData.class);
        ModLogger.debug("Registered LevelData classes");
    }
}

