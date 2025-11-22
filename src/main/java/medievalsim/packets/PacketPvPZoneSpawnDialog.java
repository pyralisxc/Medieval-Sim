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
        if (client == null || GlobalData.getCurrentState() == null) return;
        
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame)GlobalData.getCurrentState();
            if (mainGame.formManager != null) {
                PvPZoneSpawnDialog dialog = new PvPZoneSpawnDialog(client, this.zoneID, this.zoneName, this.damageMultiplier, this.combatLockSeconds);
                mainGame.formManager.addComponent((FormComponent)dialog);
            }
        }
    }
}

