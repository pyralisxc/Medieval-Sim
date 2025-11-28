/*
 * Base class for administrative zones in Medieval Sim
 * Provides common functionality for Protected and PvP zones
 */
package medievalsim.zones.domain;

import java.awt.Color;
import java.awt.Rectangle;
import necesse.engine.Settings;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

public abstract class AdminZone {
    public int uniqueID;
    public String name;
    public Zoning zoning;
    public long creatorAuth;
    public int colorHue;
    protected boolean removed = false;

    public AdminZone() {
        this.zoning = new Zoning(true);
        this.name = "";
        this.creatorAuth = -1L;
        this.colorHue = 0;
    }

    public AdminZone(int uniqueID, String name, long creatorAuth, int colorHue) {
        this.uniqueID = uniqueID;
        this.name = name;
        this.creatorAuth = creatorAuth;
        this.colorHue = colorHue;
        this.zoning = new Zoning(true);
    }

    public abstract String getTypeID();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean containsTile(int tileX, int tileY) {
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            return this.zoning.containsTile(tileX, tileY);
        }
    }

    public boolean containsPosition(float x, float y) {
        return this.containsTile(GameMath.getTileCoordinate((int)((int)x)), GameMath.getTileCoordinate((int)((int)y)));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean expand(Rectangle rectangle) {
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            return this.zoning.addRectangle(rectangle);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean shrink(Rectangle rectangle) {
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            return this.zoning.removeRectangle(rectangle);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean isEmpty() {
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            return this.zoning.isEmpty();
        }
    }

    public void remove() {
        this.removed = true;
    }

    public boolean shouldRemove() {
        return this.removed || this.isEmpty();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void addSaveData(SaveData save) {
        save.addInt("uniqueID", this.uniqueID);
        save.addUnsafeString("name", this.name);
        save.addLong("creatorAuth", this.creatorAuth);
        save.addInt("colorHue", this.colorHue);
        save.addUnsafeString("typeID", this.getTypeID());
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            this.zoning.addZoneSaveData("zoning", save);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void applyLoadData(LoadData save) {
        this.uniqueID = save.getInt("uniqueID", 0);
        this.name = save.getUnsafeString("name", "");
        this.creatorAuth = save.getLong("creatorAuth", -1L);
        this.colorHue = save.getInt("colorHue", 0);
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            this.zoning.applyZoneSaveData("zoning", save, 0, 0);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void writePacket(PacketWriter writer) {
        writer.putNextInt(this.uniqueID);
        writer.putNextString(this.name);
        writer.putNextLong(this.creatorAuth);
        writer.putNextInt(this.colorHue);
        writer.putNextString(this.getTypeID());
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            this.zoning.writeZonePacket(writer);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void readPacket(PacketReader reader) {
        this.uniqueID = reader.getNextInt();
        this.name = reader.getNextString();
        this.creatorAuth = reader.getNextLong();
        this.colorHue = reader.getNextInt();
        Zoning zoning = this.zoning;
        synchronized (zoning) {
            this.zoning.readZonePacket(reader);
        }
    }

    public Color getEdgeColor() {
        return Color.getHSBColor((float)this.colorHue / 360.0f, 0.8f, 0.6f);
    }

    public Color getFillColor() {
        Color base = Color.getHSBColor((float)this.colorHue / 360.0f, 0.8f, 0.8f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 75);
    }

    public boolean isCreator(ServerClient client) {
        return client != null && client.authentication == this.creatorAuth;
    }

    public boolean isWorldOwner(ServerClient client, Level level) {
        if (client == null || level == null || !level.isServer()) {
            return false;
        }
        return Settings.serverOwnerAuth != -1L && client.authentication == Settings.serverOwnerAuth;
    }
}

