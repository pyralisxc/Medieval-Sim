package medievalsim.banking.ui;

import java.awt.Color;
import java.awt.Rectangle;

import medievalsim.ui.UIStyleConstants;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

public class BankContainerForm<T extends BankContainer> extends ContainerForm<T> {

    private FormLabel coinLabel;
    private FormLabel upgradeLabel;
    private FormTextInput coinAmountInput;

    public BankContainerForm(Client client, T container) {
        super(client, 600, 600, container);

        FormFlow flow = new FormFlow(10);

        FormLabel titleLabel = this.addComponent(new FormLabel(
            Localization.translate("ui", "banktitle"),
            UIStyleConstants.TITLE_FONT,
            0,
            this.getWidth() / 2,
            flow.next(30)
        ));
        titleLabel.setColor(Color.BLACK);

        flow.next(10);
        int coinY = flow.next(30);

        this.coinLabel = this.addComponent(new FormLabel(
            getCoinText(container),
            UIStyleConstants.BODY_FONT,
            -1,
            10,
            coinY
        ));
        this.coinLabel.setColor(Color.BLACK);
        container.setCoinCountUpdateCallback(this::refreshCoinLabel);

        this.coinAmountInput = this.addComponent(new FormTextInput(
            200,
            coinY - 4,
            FormInputSize.SIZE_16,
            100,
            100,
            10
        ));
        this.coinAmountInput.setText("100");

        this.addComponent(new FormTextButton(
            Localization.translate("ui", "depositcoins"),
            310,
            coinY - 4,
            100,
            FormInputSize.SIZE_16,
            ButtonColor.BASE
        )).onClicked((e) -> {
            try {
                int amount = Integer.parseInt(this.coinAmountInput.getText());
                container.depositCoins.runAndSend(amount);
            } catch (NumberFormatException ex) {
                // ignore
            }
        });

        this.addComponent(new FormTextButton(
            Localization.translate("ui", "withdrawcoins"),
            420,
            coinY - 4,
            110,
            FormInputSize.SIZE_16,
            ButtonColor.BASE
        )).onClicked((e) -> {
            try {
                int amount = Integer.parseInt(this.coinAmountInput.getText());
                container.withdrawCoins.runAndSend(amount);
            } catch (NumberFormatException ex) {
                // ignore
            }
        });

        flow.next(10);
        int upgradeY = flow.next(20);

        this.upgradeLabel = this.addComponent(new FormLabel(
            getUpgradeText(container),
            UIStyleConstants.BODY_FONT,
            -1,
            10,
            upgradeY
        ));
        this.upgradeLabel.setColor(Color.BLACK);

        FormTextButton upgradeButton = this.addComponent(new FormTextButton(
            Localization.translate("ui", "bankupgrade"),
            330,
            upgradeY - 2,
            120,
            FormInputSize.SIZE_16,
            ButtonColor.BASE
        ));
        upgradeButton.onClicked((e) -> {
            if (container.canUpgrade()) {
                container.purchaseUpgrade.runAndSend();
            }
        });

        if (container.canUpgrade()) {
            int upgradeCost = container.getNextUpgradeCost();
            upgradeButton.setTooltip(
                new LocalMessage("ui", "bankupgradecost", "cost", String.valueOf(upgradeCost)).translate()
            );
        } else {
            upgradeButton.setActive(false);
            upgradeButton.setTooltip(new LocalMessage("ui", "bankmaxlevel").translate());
        }

        int pinY = 10;

        String pinButtonText = container.bank != null && container.bank.isPinSet()
            ? Localization.translate("ui", "changepin")
            : Localization.translate("ui", "setpin");

        this.addComponent(new FormTextButton(
            pinButtonText,
            10,
            pinY - 2,
            120,
            FormInputSize.SIZE_16,
            ButtonColor.BASE
        )).onClicked((e) -> {
            PinDialog pinDialog = PinDialog.createSetPinDialog(client, (pin) -> {
                container.setNewPin.runAndSend(pin);
            });
            if (this.getManager() instanceof necesse.gfx.forms.ContinueComponentManager manager) {
                manager.addContinueForm(null, pinDialog);
            }
        });

        int buttonY = 90;
        FormFlow iconFlow = new FormFlow(this.getWidth() - 4);

        FormContentIconButton sortButton = this.addComponent(new FormContentIconButton(
            iconFlow.next(-26) - 24,
            buttonY,
            FormInputSize.SIZE_24,
            ButtonColor.BASE,
            this.getInterfaceStyle().inventory_sort,
            new LocalMessage("ui", "inventorysort")
        ));
        sortButton.onClicked(e -> container.sortButton.runAndSend());
        sortButton.setCooldown(500);

        FormContentIconButton lootAllButton = this.addComponent(new FormContentIconButton(
            iconFlow.next(-26) - 24,
            buttonY,
            FormInputSize.SIZE_24,
            ButtonColor.BASE,
            this.getInterfaceStyle().container_loot_all,
            new LocalMessage("ui", "inventorylootall")
        ));
        lootAllButton.onClicked(e -> container.lootButton.runAndSend());
        lootAllButton.setCooldown(500);

        FormContentIconButton quickStackButton = this.addComponent(new FormContentIconButton(
            iconFlow.next(-26) - 24,
            buttonY,
            FormInputSize.SIZE_24,
            ButtonColor.BASE,
            this.getInterfaceStyle().inventory_quickstack_out,
            new LocalMessage("ui", "inventoryquickstack")
        ));
        quickStackButton.onClicked(e -> container.quickStackButton.runAndSend());
        quickStackButton.setCooldown(500);

        flow.next(10);
        int slotsPerRow = 10;
        int slotSize = 40;
        int startX = 10;
        int startY = Math.max(buttonY + 40, pinY + 40);
        int currentSlots = container.getCurrentSlots();

        int currentY = startY;
        for (int i = 0; i < currentSlots; i++) {
            int slotIndex = i + container.BANK_INVENTORY_START;
            int x = i % slotsPerRow;
            if (x == 0 && i > 0) {
                currentY += slotSize;
            }
            this.addComponent(new FormContainerSlot(
                client,
                container,
                slotIndex,
                startX + x * slotSize,
                currentY
            ));
        }
    }

    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        if (this.coinLabel != null && this.container != null) {
            this.coinLabel.setText(getCoinText(this.container));
        }

        if (this.upgradeLabel != null && this.container != null) {
            this.upgradeLabel.setText(getUpgradeText(this.container));
        }

        super.draw(tickManager, perspective, renderBox);
    }

    @Override
    public void onWindowResized(GameWindow window) {
        super.onWindowResized(window);
        ContainerComponent.setPosFocus(this);
    }

    @Override
    public boolean shouldOpenInventory() {
        return true;
    }

    private String getCoinText(T container) {
        long coins = container.clientCoinCount;
        return Localization.translate("ui", "bankcoins") + " " + coins;
    }

    private void refreshCoinLabel() {
        if (this.coinLabel != null) {
            this.coinLabel.setText(getCoinText(this.container));
        }
    }

    private String getUpgradeText(T container) {
        String levelText = Localization.translate("ui", "bankupgradelevel",
            "level", String.valueOf(container.currentUpgradeLevel),
            "max", String.valueOf(container.maxUpgradeLevel)
        );

        String slotsText = Localization.translate("ui", "bankslots",
            "current", String.valueOf(container.getCurrentSlots()),
            "max", String.valueOf(container.getMaxSlots())
        );

        return levelText + " | " + slotsText;
    }
}
