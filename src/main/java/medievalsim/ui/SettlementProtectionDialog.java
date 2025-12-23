/*
 * Settlement Protection Configuration Dialog
 * Allows settlement owners to configure protection settings for their settlement
 */
package medievalsim.ui;

import medievalsim.packets.PacketConfigureSettlementProtection;
import medievalsim.zones.settlement.SettlementProtectionData;
import medievalsim.zones.settlement.SettlementProtectionLevelData;
import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.ContinueComponentManager;
import necesse.gfx.forms.FormManager;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

public class SettlementProtectionDialog extends ContinueForm {
    private final Client client;
    private final int settlementTileX;
    private final int settlementTileY;
    private SettlementProtectionData protectionData;
    
    // UI Components
    private FormCheckBox enabledCheckbox;
    private FormCheckBox allowTeamCheckbox;
    private FormCheckBox canBreakCheckbox;
    private FormCheckBox canPlaceCheckbox;
    private FormCheckBox canInteractDoorsCheckbox;
    private FormCheckBox canInteractContainersCheckbox;
    private FormCheckBox canInteractStationsCheckbox;
    private FormCheckBox canInteractSignsCheckbox;
    private FormCheckBox canInteractSwitchesCheckbox;
    private FormCheckBox canInteractFurnitureCheckbox;
    private FormCheckBox disableBroomsCheckbox;
    private FormCheckBox allowBossSummonsCheckbox;
    
    public SettlementProtectionDialog(Client client, int settlementTileX, int settlementTileY) {
        super("settlementprotection", 400, 500);
        this.client = client;
        this.settlementTileX = settlementTileX;
        this.settlementTileY = settlementTileY;
        
        // Load current protection data
        loadProtectionData();
        
        // Build UI
        buildUI();
    }
    
    private void loadProtectionData() {
        Level level = client.getLevel();
        if (level == null) {
            // Create default protection data
            protectionData = new SettlementProtectionData();
            return;
        }
        
        LevelData data = level.getLevelData("settlementprotectiondata");
        if (data instanceof SettlementProtectionLevelData) {
            SettlementProtectionLevelData spld = (SettlementProtectionLevelData) data;
            protectionData = spld.getManager().getProtectionData(settlementTileX, settlementTileY);
        } else {
            // Create default protection data
            protectionData = new SettlementProtectionData();
        }
    }
    
    private void buildUI() {
        FormFlow flow = new FormFlow(10);
        
        // Title
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "settlementprotection",
            new FontOptions(20), 0, this.getWidth() / 2, 0, this.getWidth() - 20), 10));

        // Description
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "settlementprotectiondesc",
            new FontOptions(14), 0, this.getWidth() / 2, 0, this.getWidth() - 40), 10));

        // Enabled checkbox
        enabledCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "protectionenabled"),
            20, flow.next(30), this.getWidth() - 40, protectionData.isEnabled()));
        enabledCheckbox.onClicked(e -> updateProtectionState());
        
        flow.next(10);
        
        // Allow Owner's Team checkbox
        allowTeamCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "allowownsteam"), 
            20, flow.next(25), this.getWidth() - 40, protectionData.getAllowOwnerTeam()));
        allowTeamCheckbox.onClicked(e -> updateProtectionState());
        
        // Team Permissions section
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "teampermissions",
            new FontOptions(16), -1, 20, flow.next(10), this.getWidth() - 40), 5));

        // Can Break
        canBreakCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "canbreak"),
            30, flow.next(22), this.getWidth() - 50, protectionData.getCanBreak()));
        canBreakCheckbox.onClicked(e -> updateProtectionState());

        // Can Place
        canPlaceCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "canplace"),
            30, flow.next(22), this.getWidth() - 50, protectionData.getCanPlace()));
        canPlaceCheckbox.onClicked(e -> updateProtectionState());

        // Interactions section
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "interactions",
            new FontOptions(16), -1, 20, flow.next(10), this.getWidth() - 40), 5));
        
        // Can Interact Doors
        canInteractDoorsCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractdoors"), 
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractDoors()));
        canInteractDoorsCheckbox.onClicked(e -> updateProtectionState());
        
        // Can Interact Containers
        canInteractContainersCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractcontainers"), 
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractContainers()));
        canInteractContainersCheckbox.onClicked(e -> updateProtectionState());
        
        // Can Interact Stations
        canInteractStationsCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractstations"),
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractStations()));
        canInteractStationsCheckbox.onClicked(e -> updateProtectionState());

        // Can Interact Signs
        canInteractSignsCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractsigns"),
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractSigns()));
        canInteractSignsCheckbox.onClicked(e -> updateProtectionState());

        // Can Interact Switches
        canInteractSwitchesCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractswitches"),
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractSwitches()));
        canInteractSwitchesCheckbox.onClicked(e -> updateProtectionState());

        // Can Interact Furniture
        canInteractFurnitureCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "caninteractfurniture"),
            40, flow.next(22), this.getWidth() - 60, protectionData.getCanInteractFurniture()));
        canInteractFurnitureCheckbox.onClicked(e -> updateProtectionState());

        // Movement restrictions
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "movementrestrictions",
            new FontOptions(16), -1, 20, flow.next(10), this.getWidth() - 40), 5));

        disableBroomsCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "disablebrooms"),
            40, flow.next(22), this.getWidth() - 60, protectionData.isBroomRidingDisabled()));
        disableBroomsCheckbox.onClicked(e -> updateProtectionState());
        
        // Boss summon restrictions
        this.addComponent(flow.nextY(new FormLocalLabel("ui", "bosssummonrestrictions",
            new FontOptions(16), -1, 20, flow.next(10), this.getWidth() - 40), 5));
        
        allowBossSummonsCheckbox = this.addComponent(new FormCheckBox(
            Localization.translate("ui", "allowbosssummons"),
            40, flow.next(22), this.getWidth() - 60, protectionData.getAllowBossSummons()));
        allowBossSummonsCheckbox.onClicked(e -> updateProtectionState());

        flow.next(15);

        // Buttons
        int buttonY = flow.next(35);
        FormLocalTextButton saveButton = this.addComponent(new FormLocalTextButton("ui", "confirmbutton",
            20, buttonY, this.getWidth() / 2 - 25));
        saveButton.onClicked(e -> {
            sendProtectionPacket();
            closeDialog();
        });

        FormLocalTextButton cancelButton = this.addComponent(new FormLocalTextButton("ui", "cancelbutton",
            this.getWidth() / 2 + 5, buttonY, this.getWidth() / 2 - 25));
        cancelButton.onClicked(e -> closeDialog());

        // Set final height
        this.setHeight(flow.next(10));
    }

    private void updateProtectionState() {
        // Update local protection data from checkboxes
        protectionData.setEnabled(enabledCheckbox.checked);
        protectionData.setAllowOwnerTeam(allowTeamCheckbox.checked);
        protectionData.setCanBreak(canBreakCheckbox.checked);
        protectionData.setCanPlace(canPlaceCheckbox.checked);
        protectionData.setCanInteractDoors(canInteractDoorsCheckbox.checked);
        protectionData.setCanInteractContainers(canInteractContainersCheckbox.checked);
        protectionData.setCanInteractStations(canInteractStationsCheckbox.checked);
        protectionData.setCanInteractSigns(canInteractSignsCheckbox.checked);
        protectionData.setCanInteractSwitches(canInteractSwitchesCheckbox.checked);
        protectionData.setCanInteractFurniture(canInteractFurnitureCheckbox.checked);
        protectionData.setDisableBrooms(disableBroomsCheckbox.checked);
        protectionData.setAllowBossSummons(allowBossSummonsCheckbox.checked);
    }

    private void sendProtectionPacket() {
        // Send packet to server with updated protection settings
        client.network.sendPacket((Packet) new PacketConfigureSettlementProtection(
            settlementTileX,
            settlementTileY,
            protectionData.isEnabled(),
            protectionData.getAllowOwnerTeam(),
            protectionData.getCanBreak(),
            protectionData.getCanPlace(),
            protectionData.getCanInteractDoors(),
            protectionData.getCanInteractContainers(),
            protectionData.getCanInteractStations(),
            protectionData.getCanInteractSigns(),
            protectionData.getCanInteractSwitches(),
            protectionData.getCanInteractFurniture(),
            protectionData.isBroomRidingDisabled()
        ));
    }

    @Override
    protected void init() {
        super.init();

        // Center the dialog on screen
        centerDialog();
    }

    @Override
    public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager, PlayerMob perspective, java.awt.Rectangle renderBox) {
        // Check if dialog should close (container closed or game paused)
        // Close BEFORE calling super.draw() to avoid ConcurrentModificationException
        if (!this.isContinued() && (!client.hasOpenContainer() || client.isPaused())) {
            this.applyContinue();
            return; // Don't draw if we're closing
        }

        super.draw(tickManager, perspective, renderBox);
    }

    private void centerDialog() {
        // Center the dialog in the middle of the screen
        GameWindow window = WindowManager.getWindow();
        if (window != null) {
            int centerX = window.getHudWidth() / 2 - this.getWidth() / 2;
            int centerY = window.getHudHeight() / 2 - this.getHeight() / 2;
            this.setPosMiddle(centerX + this.getWidth() / 2, centerY + this.getHeight() / 2);
        }
    }

    public void openDialog() {
        // Open this dialog as a continue form using GlobalData
        FormManager formManager = GlobalData.getCurrentState().getFormManager();
        if (formManager instanceof ContinueComponentManager) {
            ((ContinueComponentManager) formManager).addContinueForm("settlementprotection", this);
        }
    }

    private void closeDialog() {
        // Close this dialog by applying continue
        this.applyContinue();
    }
}


