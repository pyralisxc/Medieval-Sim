package medievalsim.banking;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

import java.util.function.Consumer;
import medievalsim.ui.fixes.InputFocusManager;

/**
 * Simple PIN dialog for setting or entering a bank PIN.
 */
public class PinDialog extends ContinueForm {

    private FormTextInput pinInput;
    private FormTextButton confirmButton;
    private FormTextButton cancelButton;
    private Consumer<String> onConfirm;
    
    /**
     * Create a PIN dialog.
     *
     * @param title Dialog title
     * @param message Dialog message
     * @param onConfirm Callback when PIN is confirmed
     */
    public PinDialog(String title, String message, Consumer<String> onConfirm) {
        super("pindialog", 300, 150);

        this.onConfirm = onConfirm;

        FormFlow flow = new FormFlow(10);

        // Title
        this.addComponent(flow.nextY(new FormLabel(title, new FontOptions(18), 0, this.getWidth() / 2, 10, this.getWidth() - 20), 10));

        // Message
        this.addComponent(flow.nextY(new FormLabel(message, new FontOptions(14), 0, this.getWidth() / 2, 0, this.getWidth() - 20), 10));

        // PIN input (4-6 digits only)
        this.pinInput = this.addComponent(new FormTextInput(
            10,
            flow.next(32),
            FormInputSize.SIZE_24,
            this.getWidth() - 20,
            200,
            6  // Max 6 digits
        ));

        // Allow digits to be typed; validate full PIN length on submit
        // Avoid using setRegexMatchFull which blocks partial input as users type
        // Optional: Enhance focus behavior to prevent hotkey issues
        InputFocusManager.enhanceTextInput(this.pinInput);
        this.pinInput.placeHolder = new StaticMessage("4-6 digits");

        // Submit on Enter key
        this.pinInput.onSubmit(e -> {
            if (isValidPin()) {
                submitPin();
            }
        });

        flow.next(10);

        // Buttons
        int buttonY = flow.next(36);
        int buttonWidth = (this.getWidth() - 30) / 2;

        this.confirmButton = this.addComponent(new FormTextButton(
            Localization.translate("ui", "confirmbutton"),
            10,
            buttonY,
            buttonWidth,
            FormInputSize.SIZE_32,
            ButtonColor.BASE
        ));

        this.confirmButton.onClicked(e -> {
            if (isValidPin()) {
                submitPin();
            }
        });

        this.cancelButton = this.addComponent(new FormTextButton(
            Localization.translate("ui", "backbutton"),
            20 + buttonWidth,
            buttonY,
            buttonWidth,
            FormInputSize.SIZE_32,
            ButtonColor.BASE
        ));

        this.cancelButton.onClicked(e -> {
            this.applyContinue();
        });
    }
    
    /**
     * Check if the entered PIN is valid (4-6 digits).
     */
    private boolean isValidPin() {
        String pin = this.pinInput.getText();
        return pin != null && pin.matches("[0-9]{4,6}");
    }
    
    /**
     * Submit the PIN.
     */
    private void submitPin() {
        String pin = this.pinInput.getText();
        if (this.onConfirm != null) {
            this.onConfirm.accept(pin);
        }
        this.applyContinue();
    }

    /**
     * Create a "Set PIN" dialog.
     */
    public static PinDialog createSetPinDialog(Client client, Consumer<String> onConfirm) {
        return new PinDialog(
            Localization.translate("ui", "setpin"),
            Localization.translate("ui", "enternewpin"),
            onConfirm
        );
    }

    /**
     * Create an "Enter PIN" dialog.
     */
    public static PinDialog createEnterPinDialog(Client client, Consumer<String> onConfirm) {
        return new PinDialog(
            Localization.translate("ui", "pinrequired"),
            Localization.translate("ui", "enterpin"),
            onConfirm
        );
    }

    @Override
    public void onWindowResized(GameWindow window) {
        super.onWindowResized(window);
        // Center the dialog on screen
        this.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
    }

    @Override
    protected void init() {
        super.init();
        // Set focus to the PIN input when dialog is initialized
        if (this.pinInput != null) {
            this.pinInput.setTyping(true);
        }
    }
}

