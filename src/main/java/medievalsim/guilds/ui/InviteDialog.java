package medievalsim.guilds.ui;

import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import medievalsim.packets.PacketRespondInvite;

import java.util.function.Consumer;

public class InviteDialog extends ContinueForm {

    private final Client clientRef;
    private final int guildID;
    private final String guildName;
    private final long senderAuth;
    private final String senderName;

    private FormTextButton acceptButton;
    private FormTextButton declineButton;

    public InviteDialog(Client client, int guildID, String guildName, long senderAuth, String senderName) {
        super("invite_dialog", 360, 160);
        this.clientRef = client;
        this.guildID = guildID;
        this.guildName = guildName != null ? guildName : "";
        this.senderAuth = senderAuth;
        this.senderName = senderName != null ? senderName : "";

        FormFlow flow = new FormFlow(10);

        String title = Localization.translate("ui", "invitedialog.title");
        this.addComponent(flow.nextY(new FormLabel(title, new FontOptions(18), 0, this.getWidth() / 2, 6, this.getWidth() - 20), 8));

        String message = Localization.translate("message", "guild.invited", "guild", this.guildName, "sender", this.senderName);
        this.addComponent(flow.nextY(new FormLabel(message, new FontOptions(14), 0, this.getWidth() / 2, 0, this.getWidth() - 20), 8));

        int buttonY = flow.next(38);
        int buttonWidth = (this.getWidth() - 30) / 2;

        acceptButton = this.addComponent(new FormTextButton(Localization.translate("ui", "acceptbutton"), 10, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE));
        declineButton = this.addComponent(new FormTextButton(Localization.translate("ui", "declinebutton"), 20 + buttonWidth, buttonY, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE));

        // Default behavior: Accept = send accept packet, Decline = send decline packet
        acceptButton.onClicked(e -> applyResponse(true));
        declineButton.onClicked(e -> applyResponse(false));
    }

    private void applyResponse(boolean accept) {
        try {
            if (this.clientRef != null) {
                this.clientRef.network.sendPacket(new PacketRespondInvite(this.guildID, accept));
            }
        } catch (Exception e) {
            // ignore — best-effort send
        } finally {
            this.applyContinue();
        }
    }

    public static InviteDialog createInviteDialog(Client client, int guildID, String guildName, long senderAuth, String senderName) {
        return new InviteDialog(client, guildID, guildName, senderAuth, senderName);
    }

    @Override
    protected void init() {
        super.init();
        // center by default
        try {
            this.setPosMiddle(null == null ? 400 : 400, null == null ? 300 : 300);
        } catch (Exception ignored) {}
    }
}
