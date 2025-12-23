/*
 * Guild Bank for Medieval Sim Mod
 * Shared item storage for guild members with permission-based access.
 * Deposits and withdrawals are logged in the guild audit trail.
 */
package medievalsim.guilds.bank;

import medievalsim.config.ModConfig;
import medievalsim.guilds.GuildAuditEntry;
import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PermissionType;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Guild Bank - shared inventory storage for guild members.
 * 
 * Features:
 * - Multiple tabs (unlockable through research)
 * - Permission-based deposit/withdrawal
 * - Full audit logging of all transactions
 * - Daily withdrawal limits by rank
 */
public class GuildBank {

    // Bank configuration
    public static final int SLOTS_PER_TAB = 50;  // 5 rows x 10 columns
    public static final int MAX_TABS = 8;
    public static final int DEFAULT_TABS = 2;

    // The owning guild
    private final int guildID;

    // Bank tabs (each is an Inventory)
    private final List<GuildBankTab> tabs = new ArrayList<>();

    // Tab names (customizable by officers)
    private final String[] tabNames = new String[MAX_TABS];

    // Current number of unlocked tabs
    private int unlockedTabs = DEFAULT_TABS;

    public GuildBank(int guildID) {
        this.guildID = guildID;

        // Initialize default tab names
        for (int i = 0; i < MAX_TABS; i++) {
            tabNames[i] = "Tab " + (i + 1);
        }

        // Create initial tabs
        for (int i = 0; i < DEFAULT_TABS; i++) {
            tabs.add(new GuildBankTab(SLOTS_PER_TAB));
        }
    }

    // === Tab Management ===

    public int getUnlockedTabs() {
        return unlockedTabs;
    }

    public boolean unlockTab() {
        if (unlockedTabs >= MAX_TABS) {
            return false;
        }
        tabs.add(new GuildBankTab(SLOTS_PER_TAB));
        unlockedTabs++;
        return true;
    }

    public GuildBankTab getTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= unlockedTabs) {
            return null;
        }
        return tabs.get(tabIndex);
    }

    public String getTabName(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= MAX_TABS) {
            return "Invalid";
        }
        return tabNames[tabIndex];
    }

    public void setTabName(int tabIndex, String name) {
        if (tabIndex >= 0 && tabIndex < MAX_TABS) {
            tabNames[tabIndex] = name;
        }
    }

    // === Item Operations ===

    /**
     * Deposit an item into the bank.
     * @return The remaining item that couldn't fit, or null if fully deposited
     */
    public InventoryItem deposit(int tabIndex, InventoryItem item, GuildData guild, long playerAuth, String playerName) {
        GuildBankTab tab = getTab(tabIndex);
        if (tab == null || item == null) {
            return item;
        }

        int amountBefore = item.getAmount();
        InventoryItem remaining = tab.addItem(item);
        int amountDeposited = amountBefore - (remaining != null ? remaining.getAmount() : 0);

        if (amountDeposited > 0 && guild != null) {
            // Audit log
            guild.addAuditEntry(
                GuildAuditEntry.Action.BANK_DEPOSIT,
                playerAuth,
                String.format("Deposited %dx %s to %s", amountDeposited, item.item.getStringID(), getTabName(tabIndex))
            );
            ModLogger.debug("Guild %d bank: %s deposited %dx %s",
                guildID, playerName, amountDeposited, item.item.getStringID());
        }

        return remaining;
    }

    /**
     * Withdraw an item from the bank.
     * @return The withdrawn item, or null if slot was empty
     */
    public InventoryItem withdraw(int tabIndex, int slotIndex, int amount, GuildData guild, long playerAuth, String playerName) {
        GuildBankTab tab = getTab(tabIndex);
        if (tab == null) {
            return null;
        }

        InventoryItem item = tab.getItem(slotIndex);
        if (item == null) {
            return null;
        }

        // Calculate how much we can actually withdraw
        int toWithdraw = Math.min(amount, item.getAmount());
        if (toWithdraw <= 0) {
            return null;
        }

        // Create the withdrawn item
        InventoryItem withdrawn = item.copy();
        withdrawn.setAmount(toWithdraw);

        // Remove from bank
        if (toWithdraw >= item.getAmount()) {
            tab.setItem(slotIndex, null);
        } else {
            item.setAmount(item.getAmount() - toWithdraw);
        }

        if (guild != null) {
            // Audit log
            guild.addAuditEntry(
                GuildAuditEntry.Action.BANK_WITHDRAW,
                playerAuth,
                String.format("Withdrew %dx %s from %s", toWithdraw, withdrawn.item.getStringID(), getTabName(tabIndex))
            );
            ModLogger.debug("Guild %d bank: %s withdrew %dx %s",
                guildID, playerName, toWithdraw, withdrawn.item.getStringID());
        }

        return withdrawn;
    }

    /**
     * Move an item within the bank (reorganization).
     */
    public boolean moveItem(int fromTab, int fromSlot, int toTab, int toSlot) {
        GuildBankTab srcTab = getTab(fromTab);
        GuildBankTab dstTab = getTab(toTab);
        if (srcTab == null || dstTab == null) {
            return false;
        }

        InventoryItem srcItem = srcTab.getItem(fromSlot);
        InventoryItem dstItem = dstTab.getItem(toSlot);

        // Swap items
        srcTab.setItem(fromSlot, dstItem);
        dstTab.setItem(toSlot, srcItem);

        return true;
    }

    // === Treasury Operations (convenience methods delegating to GuildData) ===

    public long getTreasury(GuildManager gm) {
        GuildData guild = gm.getGuild(guildID);
        return guild != null ? guild.getTreasuryGold() : 0;
    }

    public boolean depositCoins(GuildManager gm, long amount, long playerAuth, String playerName) {
        GuildData guild = gm.getGuild(guildID);
        if (guild == null || amount <= 0) return false;

        guild.depositTreasury(amount, playerAuth);
        return true;
    }

    public boolean withdrawCoins(GuildManager gm, long amount, long playerAuth, String playerName) {
        GuildData guild = gm.getGuild(guildID);
        if (guild == null || amount <= 0) return false;

        if (guild.getTreasuryGold() < amount) {
            return false;
        }

        guild.withdrawTreasury(amount, playerAuth);
        return true;
    }

    // === Persistence ===

    public void addSaveData(SaveData save) {
        save.addInt("unlockedTabs", unlockedTabs);

        // Save tab names
        for (int i = 0; i < MAX_TABS; i++) {
            save.addUnsafeString("tabName" + i, tabNames[i]);
        }

        // Save each tab's inventory
        for (int i = 0; i < tabs.size(); i++) {
            SaveData tabSave = new SaveData("bankTab" + i);
            tabs.get(i).addSaveData(tabSave);
            save.addSaveData(tabSave);
        }
    }

    public void applyLoadData(LoadData load) {
        unlockedTabs = load.getInt("unlockedTabs", DEFAULT_TABS);

        // Load tab names
        for (int i = 0; i < MAX_TABS; i++) {
            tabNames[i] = load.getUnsafeString("tabName" + i, "Tab " + (i + 1));
        }

        // Clear and reload tabs
        tabs.clear();
        for (int i = 0; i < unlockedTabs; i++) {
            GuildBankTab tab = new GuildBankTab(SLOTS_PER_TAB);
            LoadData tabLoad = load.getFirstLoadDataByName("bankTab" + i);
            if (tabLoad != null) {
                tab.applyLoadData(tabLoad);
            }
            tabs.add(tab);
        }
    }

    // === Network ===

    public void writePacket(PacketWriter writer) {
        writer.putNextInt(unlockedTabs);

        // Write tab names
        for (int i = 0; i < unlockedTabs; i++) {
            writer.putNextString(tabNames[i]);
        }

        // Write tab contents
        for (int i = 0; i < unlockedTabs; i++) {
            tabs.get(i).writePacket(writer);
        }
    }

    public void readPacket(PacketReader reader) {
        unlockedTabs = reader.getNextInt();

        // Read tab names
        for (int i = 0; i < unlockedTabs; i++) {
            tabNames[i] = reader.getNextString();
        }

        // Read tab contents
        tabs.clear();
        for (int i = 0; i < unlockedTabs; i++) {
            GuildBankTab tab = new GuildBankTab(SLOTS_PER_TAB);
            tab.readPacket(reader);
            tabs.add(tab);
        }
    }

    /**
     * Write a single tab update (for incremental sync).
     */
    public void writeTabPacket(PacketWriter writer, int tabIndex) {
        GuildBankTab tab = getTab(tabIndex);
        if (tab != null) {
            writer.putNextBoolean(true);
            writer.putNextString(tabNames[tabIndex]);
            tab.writePacket(writer);
        } else {
            writer.putNextBoolean(false);
        }
    }
}
