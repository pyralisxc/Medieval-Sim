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

import medievalsim.ui.PvPZoneEntryDialog;
import necesse.engine.GlobalData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.components.FormComponent;

public class PacketPvPZoneEntryDialog extends PvPZoneDialogPacket {

    public PacketPvPZoneEntryDialog(byte[] data) {
        super(data);
    }

    public PacketPvPZoneEntryDialog(int zoneID, String zoneName, float damageMultiplier, int combatLockSeconds) {
        super(zoneID, zoneName, damageMultiplier, combatLockSeconds);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame)GlobalData.getCurrentState();
            PvPZoneEntryDialog dialog = new PvPZoneEntryDialog(client, this.zoneID, this.zoneName, this.damageMultiplier, this.combatLockSeconds);
            mainGame.formManager.addComponent((FormComponent)dialog);
        }
    }
}

