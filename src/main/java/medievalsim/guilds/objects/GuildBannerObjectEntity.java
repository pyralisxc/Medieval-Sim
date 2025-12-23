package medievalsim.guilds.objects;

import medievalsim.guilds.GuildSymbolDesign;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

/**
 * Entity for Guild Banner objects.
 * Stores guild ID and symbol design data.
 */
public class GuildBannerObjectEntity extends ObjectEntity {
    private int guildID = -1;
    private GuildSymbolDesign symbolDesign;
    // Track which player placed this banner (used for per-player placement limits)
    private long placedByAuth = -1L;

    public GuildBannerObjectEntity(Level level, int x, int y) {
        super(level, "guildbanner", x, y);
    }

    public int getGuildID() {
        return guildID;
    }

    public void setGuildID(int guildID) {
        this.guildID = guildID;
    }

    public GuildSymbolDesign getSymbolDesign() {
        return symbolDesign;
    }

    public void setSymbolDesign(GuildSymbolDesign design) {
        this.symbolDesign = design;
    }

    public long getPlacedByAuth() {
        return placedByAuth;
    }

    public void setPlacedByAuth(long auth) {
        this.placedByAuth = auth;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addInt("guildID", guildID);
        save.addLong("placedByAuth", placedByAuth);
        if (symbolDesign != null) {
            SaveData designData = new SaveData("symbolDesign");
            symbolDesign.addSaveData(designData);
            save.addSaveData(designData);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        this.guildID = save.getInt("guildID", -1);
        this.placedByAuth = save.getLong("placedByAuth", -1L);
        LoadData designData = save.getFirstLoadDataByName("symbolDesign");
        if (designData != null) {
            this.symbolDesign = new GuildSymbolDesign(designData);
        }
    }
}
