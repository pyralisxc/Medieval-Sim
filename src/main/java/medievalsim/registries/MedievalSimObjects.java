package medievalsim.registries;

import medievalsim.objects.PlotFlagObject;
import medievalsim.objects.PvPZoneBarrierObject;
import necesse.engine.registries.ObjectRegistry;

public class MedievalSimObjects {
    public static void registerCore() {
        ObjectRegistry.registerObject("pvpzonebarrier", new PvPZoneBarrierObject(), 0.0f, false);
        ObjectRegistry.registerObject("plotflag", new PlotFlagObject(), 0.0f, true);
    }
}

