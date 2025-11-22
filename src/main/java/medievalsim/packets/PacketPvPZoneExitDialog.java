package medievalsim.packets;
import medievalsim.ui.PvPZoneExitDialog;
import necesse.engine.GlobalData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.components.FormComponent;

public class PacketPvPZoneExitDialog
extends Packet {
    public int zoneID;
    public String zoneName;
    public int remainingCombatLockSeconds;

    public PacketPvPZoneExitDialog(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.zoneName = reader.getNextString();
        this.remainingCombatLockSeconds = reader.getNextInt();
    }

    public PacketPvPZoneExitDialog(int zoneID, String zoneName, int remainingCombatLockSeconds) {
        this.zoneID = zoneID;
        this.zoneName = zoneName;
        this.remainingCombatLockSeconds = remainingCombatLockSeconds;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextString(zoneName);
        writer.putNextInt(remainingCombatLockSeconds);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (client == null || GlobalData.getCurrentState() == null) return;
        
        if (GlobalData.getCurrentState() instanceof MainGame) {
            MainGame mainGame = (MainGame)GlobalData.getCurrentState();
            if (mainGame.formManager != null) {
                PvPZoneExitDialog dialog = new PvPZoneExitDialog(client, this.zoneID, this.zoneName, this.remainingCombatLockSeconds);
                mainGame.formManager.addComponent((FormComponent)dialog);
            }
        }
    }
}

