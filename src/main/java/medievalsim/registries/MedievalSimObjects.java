package medievalsim.registries;

import medievalsim.objects.PvPZoneBarrierObject;
import medievalsim.util.ModLogger;
import necesse.engine.registries.ObjectRegistry;

public class MedievalSimObjects {
    public static void registerCore() {
        ObjectRegistry.registerObject("pvpzonebarrier", new PvPZoneBarrierObject(), 0.0f, false);
        ModLogger.info("Registered game objects");
    }
}

