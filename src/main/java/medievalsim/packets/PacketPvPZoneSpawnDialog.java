/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.GlobalData
 *  necesse.engine.network.NetworkPacket
 *  necesse.engine.network.Packet
 *  necesse.engine.network.PacketReader
 *  necesse.engine.network.PacketWriter
 *  necesse.engine.network.client.Client
 *  necesse.engine.state.MainGame
 *  necesse.gfx.forms.components.FormComponent
 */
package medievalsim.packets;

import medievalsim.ui.PvPZoneSpawnDialog;
import medievalsim.zones.PvPZone;
import necesse.engine.GlobalData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.components.FormComponent;

public class PacketPvPZoneSpawnDialog extends PvPZoneDialogPacket {

    public PacketPvPZoneSpawnDialog(byte[] data) {
        super(data);
    }

    public PacketPvPZoneSpawnDialog(PvPZone zone) {
        super(zone.uniqueID, zone.name, zone.damageMultiplier, zone.combatLockSeconds);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame)GlobalData.getCurrentState();
            PvPZoneSpawnDialog dialog = new PvPZoneSpawnDialog(client, this.zoneID, this.zoneName, this.damageMultiplier, this.combatLockSeconds);
            mainGame.formManager.addComponent((FormComponent)dialog);
        }
    }
}

