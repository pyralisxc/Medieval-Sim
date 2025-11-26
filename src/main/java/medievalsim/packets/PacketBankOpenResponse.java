package medievalsim.packets;

import medievalsim.banking.PinDialog;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.localization.Localization;

/**
 * Server → Client: Response to a bank open request when PIN is required or invalid
 * 
 * Codes:
 * 0 - Success (no action required)
 * 1 - PIN required (client should prompt for PIN)
 * 2 - Invalid PIN (client should display error and/or prompt again)
 */
public class PacketBankOpenResponse extends Packet {

    public final int code;
    public final String message;

    public PacketBankOpenResponse(byte[] data) {
        super(data);
        PacketReader r = new PacketReader(this);
        this.code = r.getNextInt();
        this.message = r.getNextString();
    }

    public PacketBankOpenResponse(int code, String message) {
        this.code = code;
        this.message = message != null ? message : "";
        PacketWriter w = new PacketWriter(this);
        w.putNextInt(code);
        w.putNextString(this.message);
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ModLogger.debug("PacketBankOpenResponse received on client: code=%d, msg=%s", code, message);
            if (client.getLevel() != null && client.getLevel().getClient() != null) {
                String chatMessage = message != null && !message.isEmpty() ? message : (code == 1 ? Localization.translate("ui", "pinrequired") : Localization.translate("ui", "pininvalid"));
                // Colorize: code 1 = yellow/info, code 2/3 = red/error
                String colored = (code == 1) ? ("§e" + chatMessage) : ("§c" + chatMessage);
                client.getLevel().getClient().chat.addMessage(colored);

                // Try to open the PIN dialog so the player can enter their PIN immediately.
                try {
                    // Use the game client (not the network client) to access the current form manager
                    Object gameClient = client.getLevel().getClient();
                    if (gameClient != null) {
                        // Create dialog
                        PinDialog dialog = PinDialog.createEnterPinDialog(client, (pin) -> {
                            client.network.sendPacket(new PacketOpenBank(pin));
                        });

                        // Attempt to add as a continue form if possible
                        try {
                            java.lang.reflect.Method getWindowManager = gameClient.getClass().getMethod("getWindowManager");
                            Object windowManager = getWindowManager.invoke(gameClient);
                            if (windowManager != null) {
                                java.lang.reflect.Method getCurrentForm = windowManager.getClass().getMethod("getCurrentForm");
                                Object currentForm = getCurrentForm.invoke(windowManager);
                                if (currentForm != null) {
                                    java.lang.reflect.Method getManager = currentForm.getClass().getMethod("getManager");
                                    Object manager = getManager.invoke(currentForm);
                                    if (manager instanceof necesse.gfx.forms.ContinueComponentManager) {
                                        ((necesse.gfx.forms.ContinueComponentManager) manager).addContinueForm(null, dialog);
                                    }
                                }
                            }
                        } catch (NoSuchMethodException nsme) {
                            // Fall back silently if reflection paths don't exist in this runtime
                        }
                    }
                } catch (Exception e) {
                    // Don't block on UI failures — we already showed a chat message.
                    ModLogger.debug("PacketBankOpenResponse: failed to auto-open PIN dialog: %s", e.toString());
                }
            }
        } catch (Exception e) {
            ModLogger.error("PacketBankOpenResponse: client processing failed", e);
        }
    }
}
