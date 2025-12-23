package medievalsim.zones.domain;

/**
 * Enum representing the different types of admin zones.
 * Used for packet serialization and zone identification.
 */
public enum ZoneType {
    PROTECTED(0, "protected"),
    PVP(1, "pvp"),
    GUILD(2, "guild");

    private final int id;
    private final String name;

    ZoneType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static ZoneType fromId(int id) {
        for (ZoneType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return PROTECTED; // Default fallback
    }

    /**
     * Get ZoneType from an AdminZone instance.
     */
    public static ZoneType fromZone(AdminZone zone) {
        if (zone instanceof GuildZone) {
            return GUILD;
        } else if (zone instanceof PvPZone) {
            return PVP;
        }
        return PROTECTED;
    }
}
