package medievalsim.banking.service;

import medievalsim.banking.domain.PlayerBank;
import necesse.engine.network.PacketWriter;
import necesse.inventory.Inventory;

/**
 * Immutable snapshot of the state the client needs when opening the bank UI.
 */
public final class BankClientSnapshot {
    private final long ownerAuth;
    private final int upgradeLevel;
    private final boolean pinSet;
    private final long coins;
    private final Inventory inventory;

    private BankClientSnapshot(long ownerAuth, int upgradeLevel, boolean pinSet, long coins, Inventory inventory) {
        this.ownerAuth = ownerAuth;
        this.upgradeLevel = upgradeLevel;
        this.pinSet = pinSet;
        this.coins = coins;
        this.inventory = inventory;
    }

    public static BankClientSnapshot fromBank(long ownerAuth, PlayerBank bank) {
        if (bank == null) {
            return new BankClientSnapshot(ownerAuth, 0, false, 0L, new Inventory(0));
        }
        return new BankClientSnapshot(ownerAuth, bank.getUpgradeLevel(), bank.isPinSet(), bank.getCoins(), bank.getInventory());
    }

    public void writeOpenPacket(PacketWriter writer, boolean pinValidated) {
        writer.putNextLong(ownerAuth);
        writer.putNextInt(upgradeLevel);
        writer.putNextBoolean(pinValidated);
        writer.putNextBoolean(pinSet);
        writer.putNextLong(coins);
        inventory.writeContent(writer);
    }
}
