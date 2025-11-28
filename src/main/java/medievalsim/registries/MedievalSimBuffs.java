package medievalsim.registries;
import medievalsim.buffs.PvPDamageReductionBuff;
import medievalsim.buffs.PvPImmunityBuff;
import medievalsim.buffs.ProtectedZoneBuff;
import medievalsim.buffs.SettlementProtectionBuff;
import medievalsim.util.ModLogger;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class MedievalSimBuffs {
    public static void registerCore() {
        BuffRegistry.registerBuff("pvpimmunity", new PvPImmunityBuff());
        BuffRegistry.registerBuff("pvpdamagereduction", new PvPDamageReductionBuff());
        BuffRegistry.registerBuff("protectedzone", new ProtectedZoneBuff());
        BuffRegistry.registerBuff("settlementprotection", new SettlementProtectionBuff());
        ModLogger.debug("Registered %d buffs", 4);
    }
}


