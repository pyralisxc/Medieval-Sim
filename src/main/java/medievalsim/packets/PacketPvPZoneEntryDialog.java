package medievalsim.packets;
import medievalsim.ui.dialogs.PvPZoneEntryDialog;
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
        if (client == null || GlobalData.getCurrentState() == null) return;
        
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame)GlobalData.getCurrentState();
            if (mainGame.formManager != null) {
                PvPZoneEntryDialog dialog = new PvPZoneEntryDialog(client, this.zoneID, this.zoneName, this.damageMultiplier, this.combatLockSeconds);
                mainGame.formManager.addComponent((FormComponent)dialog);
            }
        }
    }
}

