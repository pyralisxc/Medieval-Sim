package medievalsim.banking;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.forms.ContainerComponent;

import java.awt.Rectangle;

/**
 * Client-side UI form for player banks.
 * Features:
 * - Bank inventory slots
 * - Coin display and deposit/withdraw buttons
 * - PIN set/change button
 * - Upgrade button
 */
public class BankContainerForm<T extends BankContainer> extends ContainerForm<T> {

    private FormLabel coinLabel;
    private FormLabel upgradeLabel;
    private FormTextInput coinAmountInput;

    public BankContainerForm(Client client, T container) {
        super(client, 600, 600, container);  // Increased height to prevent overlap

        FormFlow flow = new FormFlow(10);

        // Title
        this.addComponent(new FormLabel(
            Localization.translate("ui", "banktitle"),
            new FontOptions(20),
            0,  // Centered
            this.getWidth() / 2,
            flow.next(30)
        ));

        // Coin display and controls
        flow.next(10);
        int coinY = flow.next(30);

        // Coin label (will be updated dynamically)
        this.coinLabel = this.addComponent(new FormLabel(
            getCoinText(container),
            new FontOptions(14),
            -1,  // Left aligned
            10,
            coinY
        ));

        // Coin amount input
        this.coinAmountInput = this.addComponent(new FormTextInput(
            200,
            coinY - 4,
            FormInputSize.SIZE_16,
            100,
            100,
            10
        ));
        this.coinAmountInput.setText("100");

        // Deposit button
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
                // Invalid input
            }
        });

        // Withdraw button
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
                // Invalid input
            }
        });

        // Upgrade section - split into two rows for better layout
        flow.next(10);
        int upgradeY = flow.next(20);

        // Row 1: Upgrade label and button
        this.upgradeLabel = this.addComponent(new FormLabel(
            getUpgradeText(container),
            new FontOptions(14),
            -1,
            10,
            upgradeY
        ));

        // Upgrade button (right side of label)
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

        // Set tooltip showing upgrade cost
        if (container.canUpgrade()) {
            int upgradeCost = container.getNextUpgradeCost();
            upgradeButton.setTooltip(
                Localization.translate("ui", "bankupgradecost") + ": " + upgradeCost + " coins"
            );
        } else {
            upgradeButton.setActive(false);
            upgradeButton.setTooltip(
                Localization.translate("ui", "bankmaxlevel")
            );
        }

        // Row 2: PIN button (moved to fixed top-left position to avoid overlapping inventory)
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
            // Open PIN dialog
            PinDialog pinDialog = PinDialog.createSetPinDialog(client, (pin) -> {
                // Send PIN to server
                container.setNewPin.runAndSend(pin);
            });
            // Add to form manager as a continue form
            if (this.getManager() instanceof necesse.gfx.forms.ContinueComponentManager) {
                ((necesse.gfx.forms.ContinueComponentManager) this.getManager()).addContinueForm(null, pinDialog);
            }
        });

        // Inventory management buttons - positioned at top right
        int buttonY = 90;  // Fixed position at top right, below upgrade controls
        FormFlow iconFlow = new FormFlow(this.getWidth() - 4);

        // Sort button
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

        // Loot All button
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

        // Quick Stack button
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

        // Bank inventory slots - start below the highest of the right-side icon buttons or PIN button
        flow.next(10);
        int slotsPerRow = 10;
        int slotSize = 40;
        int startX = 10;
        int startY = Math.max(buttonY + 40, pinY + 40);  // Start below the top-right buttons or PIN button
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
        // Update coin label in real-time
        if (this.coinLabel != null && this.container != null) {
            this.coinLabel.setText(getCoinText((T) this.container));
        }

        // Update upgrade label in real-time
        if (this.upgradeLabel != null && this.container != null) {
            this.upgradeLabel.setText(getUpgradeText((T) this.container));
        }

        super.draw(tickManager, perspective, renderBox);
    }

    @Override
    public void onWindowResized(GameWindow window) {
        super.onWindowResized(window);
        // Position the form above the player inventory (prevents overlap)
        ContainerComponent.setPosFocus(this);
    }

    @Override
    public boolean shouldOpenInventory() {
        // Always open player inventory when bank is opened
        return true;
    }

    /**
     * Get coin display text.
     */
    private String getCoinText(T container) {
        // Use clientCoinCount which is synced from server
        long coins = container.clientCoinCount;
        return Localization.translate("ui", "bankcoins") + " " + coins;
    }

    /**
     * Get upgrade display text.
     */
    private String getUpgradeText(T container) {
        // Use Localization.translate with replacements for placeholders
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

