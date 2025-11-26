package medievalsim.zones;

import java.awt.Rectangle;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;

public class PvPZone
extends AdminZone {
    public static final String TYPE_ID = "pvp";
        public float damageMultiplier = ModConfig.Zones.defaultDamageMultiplier;
        public int combatLockSeconds = ModConfig.Zones.defaultCombatLockSeconds;
        // New: multipliers to control DoT behaviour inside PvP zones
        // Multiplies per-tick DoT damage (1.0 = unchanged)
        public float dotDamageMultiplier = 1.0f;
        // Multiplies DoT accumulation interval (1.0 = unchanged). >1 means ticks accumulate slower (i.e. slower DoT)
        public float dotIntervalMultiplier = 1.0f;

    public PvPZone() {
    }

    public PvPZone(int uniqueID, String name, long creatorAuth, int colorHue) {
        super(uniqueID, name, creatorAuth, colorHue);
    }

    public PvPZone(int uniqueID, String name, long creatorAuth, int colorHue, float damageMultiplier, int combatLockSeconds) {
        super(uniqueID, name, creatorAuth, colorHue);
        this.damageMultiplier = damageMultiplier;
        this.combatLockSeconds = combatLockSeconds;
    }

    /**
     * Format a damage multiplier (e.g. 0.05) into a compact, human-friendly percent string.
     * - For values >= 1% returns an integer percent ("5%").
     * - For values between 0% (exclusive) and 1% returns one decimal place ("0.5%").
     * - For exactly 0 returns "0%".
     */
    public static String formatDamagePercent(float damageMultiplier) {
        float percent = damageMultiplier * 100.0f;
        if (percent <= 0.0f) return "0%";
        if (percent >= 1.0f) {
            // Show integer percent for larger values
            return String.format(java.util.Locale.ROOT, "%d%%", Math.round(percent));
        }
        // Show one decimal place for sub-1% values (e.g. 0.5%)
        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }

    @Override
    public String getTypeID() {
        return TYPE_ID;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addFloat("damageMultiplier", this.damageMultiplier);
        save.addFloat("dotDamageMultiplier", this.dotDamageMultiplier);
        save.addFloat("dotIntervalMultiplier", this.dotIntervalMultiplier);
        save.addInt("combatLockSeconds", this.combatLockSeconds);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        this.damageMultiplier = save.getFloat("damageMultiplier", ModConfig.Zones.defaultDamageMultiplier);
        this.dotDamageMultiplier = save.getFloat("dotDamageMultiplier", 1.0f);
        this.dotIntervalMultiplier = save.getFloat("dotIntervalMultiplier", 1.0f);
        // Use the centralized default so saved defaults follow configuration values
        this.combatLockSeconds = save.getInt("combatLockSeconds", ModConfig.Zones.defaultCombatLockSeconds);
    }

    @Override
    public void writePacket(PacketWriter writer) {
        super.writePacket(writer);
        writer.putNextFloat(this.damageMultiplier);
        writer.putNextFloat(this.dotDamageMultiplier);
        writer.putNextFloat(this.dotIntervalMultiplier);
        writer.putNextInt(this.combatLockSeconds);
    }

    @Override
    public void readPacket(PacketReader reader) {
        super.readPacket(reader);
        this.damageMultiplier = reader.getNextFloat();
        this.dotDamageMultiplier = reader.getNextFloat();
        this.dotIntervalMultiplier = reader.getNextFloat();
        this.combatLockSeconds = reader.getNextInt();
    }

    public void createBarriers(Level level) {
        if (level != null && level.isServer()) {
            PvPZoneBarrierManager.createBarrier(level, this);
        }
    }

    public void updateBarriers(Level level) {
        if (level != null && level.isServer()) {
            PvPZoneBarrierManager.updateBarrier(level, this);
        }
    }

    public void removeBarriers(Level level) {
        if (level != null && level.isServer()) {
            PvPZoneBarrierManager.removeBarrier(level, this);
        }
    }

    @Override
    public boolean expand(Rectangle rectangle) {
        boolean changed = super.expand(rectangle);
        return changed;
    }

    @Override
    public boolean shrink(Rectangle rectangle) {
        boolean changed = super.shrink(rectangle);
        return changed;
    }

    /**
     * Helper method to snapshot zone edges before modification.
     * Used for differential barrier updates.
     */
    private java.util.Map<Integer, java.util.Collection<java.awt.Point>> snapshotEdges() {
        java.util.Map<Integer, java.util.Collection<java.awt.Point>> snapshot = new java.util.HashMap<>();
        try {
            necesse.engine.util.PointHashSet edge = this.zoning.getEdgeTiles();
            if (edge != null) {
                java.util.List<java.awt.Point> points = new java.util.ArrayList<>();
                for (Object o : edge) {
                    if (o instanceof java.awt.Point) {
                        points.add(new java.awt.Point((java.awt.Point)o));
                    }
                }
                snapshot.put(this.uniqueID, points);
            }
        } catch (Exception e) {
            // Best-effort operation; log details when verbose debug is enabled
            if (ModConfig.Logging.verboseDebug) {
                ModLogger.debug("Warning while snapshotting edges for PvPZone %d: %s",
                               this.uniqueID, e.getMessage());
            }
        }
        return snapshot;
    }

    /**
     * Expand the zone and update barriers using a differential update.
     * Returns true if the zone changed.
     */
    public boolean expandAndUpdateBarriers(Level level, Rectangle rectangle) {
        java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesSnapshot = snapshotEdges();

        boolean changed = this.expand(rectangle);
        if (changed && level != null && level.isServer()) {
            AdminZonesLevelData data = AdminZonesLevelData.getZoneData(level, false);
            if (data != null) {
                data.resolveAfterZoneChange(this, level, null, false, oldEdgesSnapshot);
            }
        }
        return changed;
    }

    /**
     * Shrink the zone and update barriers using a differential update.
     * Returns true if the zone changed.
     */
    public boolean shrinkAndUpdateBarriers(Level level, Rectangle rectangle) {
        java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesSnapshot = snapshotEdges();

        boolean changed = this.shrink(rectangle);
        if (changed && level != null && level.isServer()) {
            AdminZonesLevelData data = AdminZonesLevelData.getZoneData(level, false);
            if (data != null) {
                data.resolveAfterZoneChange(this, level, null, false, oldEdgesSnapshot);
            }
        }
        return changed;
    }
}

