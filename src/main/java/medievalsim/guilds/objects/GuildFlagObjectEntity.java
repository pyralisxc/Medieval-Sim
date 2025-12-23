package medievalsim.guilds.objects;

import medievalsim.guilds.GuildSymbolDesign;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.ObjectDamagedTextureArray;
import necesse.level.maps.Level;

/**
 * Object entity for guild flag - stores guild data and symbol design.
 * Links to the settlement at this location.
 */
public class GuildFlagObjectEntity extends ObjectEntity {
    
    private int guildID = -1;
    private GuildSymbolDesign symbolDesign = null;
    private final ObjectDamagedTextureArray mapTexture;

    public GuildFlagObjectEntity(Level level, int x, int y, ObjectDamagedTextureArray mapTexture) {
        super(level, "guildflag", x, y);
        this.mapTexture = mapTexture;
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

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addInt("guildID", guildID);
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
        LoadData designData = save.getFirstLoadDataByName("symbolDesign");
        if (designData != null) {
            this.symbolDesign = new GuildSymbolDesign(designData);
        }
    }

    @Override
    public boolean shouldDrawOnMap() {
        // Guild flags should appear on map like settlement flags
        return true;
    }
}
