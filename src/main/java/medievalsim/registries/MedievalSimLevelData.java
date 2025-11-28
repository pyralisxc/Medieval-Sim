package medievalsim.registries;
import medievalsim.banking.domain.BankingLevelData;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.settlement.SettlementProtectionLevelData;
import necesse.engine.registries.LevelDataRegistry;

public class MedievalSimLevelData {
    public static void registerCore() {
        LevelDataRegistry.registerLevelData((String)"adminzonesdata", AdminZonesLevelData.class);
        LevelDataRegistry.registerLevelData((String)"settlementprotectiondata", SettlementProtectionLevelData.class);
        LevelDataRegistry.registerLevelData((String)"bankingdata", BankingLevelData.class);
        LevelDataRegistry.registerLevelData((String)"grandexchangedata", medievalsim.grandexchange.domain.GrandExchangeLevelData.class);
        ModLogger.debug("Registered %d LevelData classes", 4);
    }
}

