package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.guilds.client.ClientGuildManager;
import necesse.engine.GlobalData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.localization.Localization;
import necesse.engine.state.MainGame;
import necesse.gfx.forms.ContinueComponentManager;
import necesse.gfx.forms.FormManager;
import medievalsim.guilds.ui.InviteDialog;

/**
 * Server -> Client: Notify a player that they've been invited to a guild
 */
public class PacketGuildInvited extends Packet {
    public int guildID;
    public String guildName;
    public long senderAuth;
    public String senderName;

    public PacketGuildInvited(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        this.guildID = r.getNextInt();
        this.guildName = r.getNextString();
        this.senderAuth = r.getNextLong();
        this.senderName = r.getNextString();
    }

    public PacketGuildInvited(int guildID, String guildName, long senderAuth, String senderName) {
        this.guildID = guildID;
        this.guildName = guildName != null ? guildName : "";
        this.senderAuth = senderAuth;
        this.senderName = senderName != null ? senderName : "";
        PacketWriter w = new PacketWriter(this);
        w.putNextInt(this.guildID);
        w.putNextString(this.guildName);
        w.putNextLong(this.senderAuth);
        w.putNextString(this.senderName);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ModLogger.debug("PacketGuildInvited received: guild=%d name=%s from=%s", guildID, guildName, senderName);
            
            // Store pending invite in ClientGuildManager
            ClientGuildManager.get().setPendingInvite(guildID, guildName, senderAuth, senderName);
            
            // Show chat notification
            client.chat.addMessage("\u00a7e" + senderName + " has invited you to join \u00a76" + guildName + "\u00a7e!");
            
            // Open invite dialog using proper FormManager pattern
            if (GlobalData.getCurrentState() instanceof MainGame) {
                MainGame mainGame = (MainGame) GlobalData.getCurrentState();
                FormManager formManager = mainGame.getFormManager();
                
                if (formManager instanceof ContinueComponentManager) {
                    InviteDialog dialog = InviteDialog.createInviteDialog(client, guildID, guildName, senderAuth, senderName);
                    ((ContinueComponentManager) formManager).addContinueForm("guildinvite", dialog);
                } else {
                    ModLogger.debug("PacketGuildInvited: FormManager is not a ContinueComponentManager");
                }
            }
        } catch (Exception e) {
            ModLogger.error("PacketGuildInvited: client processing failed", e);
        }
    }
}
