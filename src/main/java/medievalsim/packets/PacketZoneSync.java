package medievalsim.packets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import medievalsim.ui.AdminToolsHudForm;
import medievalsim.ui.AdminToolsHudManager;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;

public class PacketZoneSync
extends Packet {
    public List<ZoneData> protectedZones;
    public List<ZoneData> pvpZones;

    public PacketZoneSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        int protectedCount = reader.getNextShortUnsigned();
        this.protectedZones = new ArrayList<ZoneData>(protectedCount);
        for (int i = 0; i < protectedCount; ++i) {
            ZoneData zoneData = new ZoneData();
            zoneData.read(reader, false);
            this.protectedZones.add(zoneData);
        }
        int pvpCount = reader.getNextShortUnsigned();
        this.pvpZones = new ArrayList<ZoneData>(pvpCount);
        for (int i = 0; i < pvpCount; ++i) {
            ZoneData zoneData = new ZoneData();
            zoneData.read(reader, true);
            this.pvpZones.add(zoneData);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PacketZoneSync(AdminZonesLevelData zoneData) {
        this(zoneData, null);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public PacketZoneSync(AdminZonesLevelData zoneData, Server server) {
        this.protectedZones = new ArrayList<ZoneData>();
        this.pvpZones = new ArrayList<ZoneData>();

        // Work with the correctly-typed internal maps
        // Extend synchronized block to cover both owner name refresh AND zone data extraction
        Map<Integer, ProtectedZone> protectedMap = zoneData.getProtectedZonesInternal();
        synchronized (protectedMap) {
            // Refresh owner names for online players before extracting zone data
            if (server != null) {
                for (ProtectedZone zone : protectedMap.values()) {
                    if (zone.getOwnerAuth() != -1L) {
                        ServerClient ownerClient = server.getClientByAuth(zone.getOwnerAuth());
                        if (ownerClient != null) {
                            // Owner is online - refresh name
                            zone.setOwnerName(ownerClient.getName());
                        }
                    }
                }
            }

            // Extract zone data while still holding the lock
            for (ProtectedZone protectedZone : protectedMap.values()) {
                this.protectedZones.add(new ZoneData(protectedZone));
            }
        }

        Map<Integer, PvPZone> pvpMap = zoneData.getPvPZonesInternal();
        synchronized (pvpMap) {
            for (PvPZone pvPZone : pvpMap.values()) {
                this.pvpZones.add(new ZoneData(pvPZone));
            }
        }
        PacketWriter writer = new PacketWriter((Packet)this);
        if (this.protectedZones.size() > 65535) {
            throw new IllegalStateException("Too many protected zones to sync: " + this.protectedZones.size() + " (max 65535)");
        }
        if (this.pvpZones.size() > 65535) {
            throw new IllegalStateException("Too many PVP zones to sync: " + this.pvpZones.size() + " (max 65535)");
        }
        writer.putNextShortUnsigned(this.protectedZones.size());
        for (ZoneData zoneData2 : this.protectedZones) {
            zoneData2.write(writer, false);
        }
        writer.putNextShortUnsigned(this.pvpZones.size());
        for (ZoneData zoneData3 : this.pvpZones) {
            zoneData3.write(writer, true);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Zoning zoning;
        PacketReader zoningReader;
        Packet tempPacket;
        AdminZone zone;
        Level level = client.getLevel();
        if (level == null) {
            return;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, true);
        if (zoneData == null) {
            return;
        }
        zoneData.clearProtectedZones();
        zoneData.clearPvPZones();
        for (ZoneData data : this.protectedZones) {
            zone = new ProtectedZone(data.uniqueID, data.name, data.creatorAuth, data.colorHue);
            tempPacket = new Packet(data.zoningData);
            zoningReader = new PacketReader(tempPacket);
            zoning = zone.zoning;
            synchronized (zoning) {
                zone.zoning.readZonePacket(zoningReader);
            }
            if (zone instanceof ProtectedZone) {
                ProtectedZone pz = (ProtectedZone)zone;
                // Apply team IDs if present
                if (data.allowedTeamIDs != null) {
                    pz.allowedTeamIDs.addAll(data.allowedTeamIDs);
                }
                // Apply owner and permission fields (always for protected zones)
                pz.setOwnerAuth(data.ownerAuth);
                pz.setOwnerName(data.ownerName);
                pz.setAllowOwnerTeam(data.allowOwnerTeam);
                pz.setCanBreak(data.canBreak);
                pz.setCanPlace(data.canPlace);
                
                // Enhancement #5: Apply 6 granular interaction permissions
                pz.setCanInteractDoors(data.canInteractDoors);
                pz.setCanInteractContainers(data.canInteractContainers);
                pz.setCanInteractStations(data.canInteractStations);
                pz.setCanInteractSigns(data.canInteractSigns);
                pz.setCanInteractSwitches(data.canInteractSwitches);
                pz.setCanInteractFurniture(data.canInteractFurniture);
                pz.setDisableBrooms(data.disableBrooms);
            }
            zoneData.putProtectedZone((ProtectedZone)zone);
        }
        for (ZoneData data : this.pvpZones) {
            zone = new PvPZone(data.uniqueID, data.name, data.creatorAuth, data.colorHue, data.damageMultiplier, data.combatLockSeconds);
            tempPacket = new Packet(data.zoningData);
            zoningReader = new PacketReader(tempPacket);
            zoning = ((PvPZone)zone).zoning;
            synchronized (zoning) {
                ((PvPZone)zone).zoning.readZonePacket(zoningReader);
            }
            zoneData.putPvPZone((PvPZone)zone);
        }
        AdminToolsHudForm hudForm = AdminToolsHudManager.getHudForm();
        if (hudForm != null) {
            HashMap<Integer, ProtectedZone> protectedMap = new HashMap<Integer, ProtectedZone>();
            HashMap<Integer, PvPZone> pvpMap = new HashMap<Integer, PvPZone>();

            // Safely retrieve protected zones with null checks
            for (ZoneData data : this.protectedZones) {
                if (data == null) {
                    ModLogger.warn("Null ZoneData in protectedZones list during sync");
                    continue;
                }
                AdminZone zone2 = zoneData.getProtectedZones().get(data.uniqueID);
                if (zone2 == null) {
                    ModLogger.debug("Protected zone %d not found in level data during sync", data.uniqueID);
                    continue;
                }
                if (!(zone2 instanceof ProtectedZone)) {
                    ModLogger.error("Zone %d is not a ProtectedZone (type: %s)", data.uniqueID, zone2.getClass().getName());
                    continue;
                }
                protectedMap.put(zone2.uniqueID, (ProtectedZone)zone2);
            }

            // Safely retrieve PvP zones with null checks
            for (ZoneData data : this.pvpZones) {
                if (data == null) {
                    ModLogger.warn("Null ZoneData in pvpZones list during sync");
                    continue;
                }
                AdminZone zone2 = zoneData.getPvPZones().get(data.uniqueID);
                if (zone2 == null) {
                    ModLogger.debug("PvP zone %d not found in level data during sync", data.uniqueID);
                    continue;
                }
                if (!(zone2 instanceof PvPZone)) {
                    ModLogger.error("Zone %d is not a PvPZone (type: %s)", data.uniqueID, zone2.getClass().getName());
                    continue;
                }
                pvpMap.put(((PvPZone)zone2).uniqueID, (PvPZone)zone2);
            }

            hudForm.updateZones(protectedMap, pvpMap);
        }
    }

    public static class ZoneData {
        public int uniqueID;
        public String name;
        public long creatorAuth;
        public int colorHue;
        public byte[] zoningData;
        public HashSet<Integer> allowedTeamIDs;
        public float damageMultiplier;
        public int combatLockSeconds;
        // Protected zone fields
        public long ownerAuth;
        public String ownerName;
        public boolean allowOwnerTeam;
        public boolean canBreak;
        public boolean canPlace;
        
        // Enhancement #5: 6 granular interaction permissions
        public boolean canInteractDoors;
        public boolean canInteractContainers;
        public boolean canInteractStations;
        public boolean canInteractSigns;
        public boolean canInteractSwitches;
        public boolean canInteractFurniture;
        public boolean disableBrooms;

        public ZoneData() {
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public ZoneData(AdminZone zone) {
            this.uniqueID = zone.uniqueID;
            this.name = zone.name;
            this.creatorAuth = zone.creatorAuth;
            this.colorHue = zone.colorHue;
            Packet tempPacket = new Packet();
            PacketWriter zoningWriter = new PacketWriter(tempPacket);
            Zoning zoning = zone.zoning;
            synchronized (zoning) {
                zone.zoning.writeZonePacket(zoningWriter);
            }
            this.zoningData = tempPacket.getPacketData();
            if (zone instanceof ProtectedZone) {
                ProtectedZone protectedZone = (ProtectedZone)zone;
                this.allowedTeamIDs = new HashSet<Integer>(protectedZone.allowedTeamIDs);
                this.ownerAuth = protectedZone.getOwnerAuth();
                this.ownerName = protectedZone.getOwnerName();
                this.allowOwnerTeam = protectedZone.getAllowOwnerTeam();
                this.canBreak = protectedZone.getCanBreak();
                this.canPlace = protectedZone.getCanPlace();
                
                // Enhancement #5: Read 6 granular interaction permissions
                this.canInteractDoors = protectedZone.getCanInteractDoors();
                this.canInteractContainers = protectedZone.getCanInteractContainers();
                this.canInteractStations = protectedZone.getCanInteractStations();
                this.canInteractSigns = protectedZone.getCanInteractSigns();
                this.canInteractSwitches = protectedZone.getCanInteractSwitches();
                this.canInteractFurniture = protectedZone.getCanInteractFurniture();
                this.disableBrooms = protectedZone.isBroomRidingDisabled();
            }
            if (zone instanceof PvPZone) {
                PvPZone pvpZone = (PvPZone)zone;
                this.damageMultiplier = pvpZone.damageMultiplier;
                this.combatLockSeconds = pvpZone.combatLockSeconds;
            }
        }

        public void write(PacketWriter writer, boolean isPvPZone) {
            writer.putNextInt(this.uniqueID);
            writer.putNextString(this.name);
            writer.putNextLong(this.creatorAuth);
            writer.putNextInt(this.colorHue);
            writer.putNextInt(this.zoningData.length);
            writer.putNextBytes(this.zoningData);
            if (this.allowedTeamIDs != null) {
                writer.putNextShortUnsigned(this.allowedTeamIDs.size());
                for (int teamID : this.allowedTeamIDs) {
                    writer.putNextInt(teamID);
                }
            } else {
                writer.putNextShortUnsigned(0);
            }
            if (isPvPZone) {
                writer.putNextFloat(this.damageMultiplier);
                writer.putNextInt(this.combatLockSeconds);
            } else {
                // Protected zone fields
                writer.putNextLong(this.ownerAuth);
                writer.putNextString(this.ownerName);
                writer.putNextBoolean(this.allowOwnerTeam);
                writer.putNextBoolean(this.canBreak);
                writer.putNextBoolean(this.canPlace);
                
                // Enhancement #5: Write 6 granular interaction permissions
                writer.putNextBoolean(this.canInteractDoors);
                writer.putNextBoolean(this.canInteractContainers);
                writer.putNextBoolean(this.canInteractStations);
                writer.putNextBoolean(this.canInteractSigns);
                writer.putNextBoolean(this.canInteractSwitches);
                writer.putNextBoolean(this.canInteractFurniture);
                writer.putNextBoolean(this.disableBrooms);
            }
        }

        public void read(PacketReader reader, boolean isPvPZone) {
            this.uniqueID = reader.getNextInt();
            this.name = reader.getNextString();
            this.creatorAuth = reader.getNextLong();
            this.colorHue = reader.getNextInt();
            int zoningDataLength = reader.getNextInt();
            this.zoningData = reader.getNextBytes(zoningDataLength);
            int teamCount = reader.getNextShortUnsigned();
            if (teamCount > 0) {
                this.allowedTeamIDs = new HashSet<Integer>();
                for (int i = 0; i < teamCount; ++i) {
                    this.allowedTeamIDs.add(reader.getNextInt());
                }
            }
            if (isPvPZone) {
                this.damageMultiplier = reader.getNextFloat();
                this.combatLockSeconds = reader.getNextInt();
            } else {
                // Protected zone fields
                this.ownerAuth = reader.getNextLong();
                this.ownerName = reader.getNextString();
                this.allowOwnerTeam = reader.getNextBoolean();
                this.canBreak = reader.getNextBoolean();
                this.canPlace = reader.getNextBoolean();
                
                // Enhancement #5: Read 6 granular interaction permissions
                this.canInteractDoors = reader.getNextBoolean();
                this.canInteractContainers = reader.getNextBoolean();
                this.canInteractStations = reader.getNextBoolean();
                this.canInteractSigns = reader.getNextBoolean();
                this.canInteractSwitches = reader.getNextBoolean();
                this.canInteractFurniture = reader.getNextBoolean();
                this.disableBrooms = reader.getNextBoolean();
            }
        }
    }
}

