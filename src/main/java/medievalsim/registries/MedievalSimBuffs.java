package medievalsim.registries;
import medievalsim.buffs.PvPDamageReductionBuff;
import medievalsim.buffs.PvPImmunityBuff;
import medievalsim.buffs.ProtectedZoneBuff;
import medievalsim.util.ModLogger;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class MedievalSimBuffs {
    public static void registerCore() {
        BuffRegistry.registerBuff((String)"pvpimmunity", (Buff)new PvPImmunityBuff());
        BuffRegistry.registerBuff((String)"pvpdamagereduction", (Buff)new PvPDamageReductionBuff());
        BuffRegistry.registerBuff((String)"protectedzone", (Buff)new ProtectedZoneBuff());
        ModLogger.debug("Registered %d buffs", 3);
    }
}


