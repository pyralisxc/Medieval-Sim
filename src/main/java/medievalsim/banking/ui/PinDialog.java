package medievalsim.banking.ui;

import java.util.function.Consumer;

import medievalsim.ui.fixes.InputFocusManager;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

public class PinDialog extends ContinueForm {

    private FormTextInput pinInput;
    private FormTextButton confirmButton;
    private FormTextButton cancelButton;
    private Consumer<String> onConfirm;

    public PinDialog(String title, String message, Consumer<String> onConfirm) {
        super("pindialog", 300, 150);

        this.onConfirm = onConfirm;

        FormFlow flow = new FormFlow(10);

        this.addComponent(flow.nextY(new FormLabel(title, new FontOptions(18), 0, this.getWidth() / 2, 10, this.getWidth() - 20), 10));
        this.addComponent(flow.nextY(new FormLabel(message, new FontOptions(14), 0, this.getWidth() / 2, 0, this.getWidth() - 20), 10));

        this.pinInput = this.addComponent(new FormTextInput(
            10,
            flow.next(32),
            FormInputSize.SIZE_24,
            this.getWidth() - 20,
            200,
            6
        ));

        InputFocusManager.enhanceTextInput(this.pinInput);
        this.pinInput.placeHolder = new StaticMessage("4-6 digits");
        this.pinInput.onSubmit(e -> {
            if (isValidPin()) {
                submitPin();
            }
        });

        flow.next(10);

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

        this.cancelButton.onClicked(e -> this.applyContinue());
    }

    private boolean isValidPin() {
        String pin = this.pinInput.getText();
        return pin != null && pin.matches("[0-9]{4,6}");
    }

    private void submitPin() {
        String pin = this.pinInput.getText();
        if (this.onConfirm != null) {
            this.onConfirm.accept(pin);
        }
        this.applyContinue();
    }

    public static PinDialog createSetPinDialog(Client client, Consumer<String> onConfirm) {
        return new PinDialog(
            Localization.translate("ui", "setpin"),
            Localization.translate("ui", "enternewpin"),
            onConfirm
        );
    }

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
        this.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
    }

    @Override
    protected void init() {
        super.init();
        if (this.pinInput != null) {
            this.pinInput.setTyping(true);
        }
    }
}
