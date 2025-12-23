/*
 * Research Effect for Medieval Sim Mod
 * Defines the bonus effect granted when a research project is completed.
 */
package medievalsim.guilds.research;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;

/**
 * Represents the effect/bonus of a completed research project.
 */
public class ResearchEffect {

    public enum EffectType {
        // Bank upgrades
        BANK_TAB_UNLOCK,        // Unlocks additional bank tab
        BANK_SLOT_INCREASE,     // Increases slots per tab

        // Treasury upgrades
        TREASURY_CAPACITY,      // Increases max treasury (if capped)
        TREASURY_INTEREST,      // Daily interest on treasury

        // Territory upgrades
        TERRITORY_SIZE,         // Increases max zone tiles
        TERRITORY_PLOTS,        // Increases max plots per guild

        // Member benefits
        MEMBER_CAPACITY,        // Increases max members
        MEMBER_WITHDRAW_LIMIT,  // Increases daily withdrawal limit

        // Combat bonuses
        COMBAT_DAMAGE_BONUS,    // % damage increase in guild zones
        COMBAT_DEFENSE_BONUS,   // % defense increase in guild zones
        COMBAT_REGEN_BONUS,     // HP regen in guild zones

        // Economic bonuses
        CRAFTING_SPEED,         // % faster crafting in guild zones
        GATHERING_BONUS,        // % more resources gathered
        SHOP_DISCOUNT,          // % discount at guild shops
        TAX_REDUCTION,          // % reduction in guild tax

        // Special unlocks
        UNLOCK_GUILD_SHOP,      // Enables guild shop feature
        UNLOCK_ALLY_SYSTEM,     // Enables guild alliances
        UNLOCK_GUILD_QUESTS,    // Enables guild quest board
        UNLOCK_GUILD_EVENTS,    // Enables guild events

        // Passive generation
        PASSIVE_INCOME,         // Daily coin generation
        PASSIVE_RESEARCH,       // Passive research point generation

        // Misc
        CUSTOM                  // For modular/scripted effects
    }

    private final EffectType type;
    private final float value;           // Bonus value (percentage or flat)
    private final boolean isPercentage;  // true = %, false = flat value
    private final String customData;     // Additional data for CUSTOM type

    public ResearchEffect(EffectType type, float value, boolean isPercentage) {
        this(type, value, isPercentage, null);
    }

    public ResearchEffect(EffectType type, float value, boolean isPercentage, String customData) {
        this.type = type;
        this.value = value;
        this.isPercentage = isPercentage;
        this.customData = customData;
    }

    public EffectType getType() {
        return type;
    }

    public float getValue() {
        return value;
    }

    public boolean isPercentage() {
        return isPercentage;
    }

    public String getCustomData() {
        return customData;
    }

    /**
     * Get display string for the effect.
     */
    public String getDisplayString() {
        String valueStr = isPercentage ? String.format("+%.1f%%", value) : String.format("+%.0f", value);

        return switch (type) {
            case BANK_TAB_UNLOCK -> "Unlock bank tab";
            case BANK_SLOT_INCREASE -> valueStr + " bank slots";
            case TREASURY_CAPACITY -> valueStr + " treasury capacity";
            case TREASURY_INTEREST -> valueStr + " daily interest";
            case TERRITORY_SIZE -> valueStr + " territory tiles";
            case TERRITORY_PLOTS -> valueStr + " territory plots";
            case MEMBER_CAPACITY -> valueStr + " member slots";
            case MEMBER_WITHDRAW_LIMIT -> valueStr + " daily withdraw limit";
            case COMBAT_DAMAGE_BONUS -> valueStr + " damage in territory";
            case COMBAT_DEFENSE_BONUS -> valueStr + " defense in territory";
            case COMBAT_REGEN_BONUS -> valueStr + " HP regen in territory";
            case CRAFTING_SPEED -> valueStr + " crafting speed";
            case GATHERING_BONUS -> valueStr + " gathering yield";
            case SHOP_DISCOUNT -> valueStr + " shop discount";
            case TAX_REDUCTION -> valueStr + " tax reduction";
            case UNLOCK_GUILD_SHOP -> "Unlocks Guild Shop";
            case UNLOCK_ALLY_SYSTEM -> "Unlocks Guild Alliances";
            case UNLOCK_GUILD_QUESTS -> "Unlocks Guild Quests";
            case UNLOCK_GUILD_EVENTS -> "Unlocks Guild Events";
            case PASSIVE_INCOME -> valueStr + " coins/day";
            case PASSIVE_RESEARCH -> valueStr + " research/day";
            case CUSTOM -> customData != null ? customData : "Custom Effect";
        };
    }

    // === Network ===

    public void writePacket(PacketWriter writer) {
        writer.putNextInt(type.ordinal());
        writer.putNextFloat(value);
        writer.putNextBoolean(isPercentage);
        writer.putNextString(customData != null ? customData : "");
    }

    public static ResearchEffect readPacket(PacketReader reader) {
        int typeOrdinal = reader.getNextInt();
        EffectType type = EffectType.values()[typeOrdinal];
        float value = reader.getNextFloat();
        boolean isPercentage = reader.getNextBoolean();
        String customData = reader.getNextString();
        if (customData.isEmpty()) customData = null;

        return new ResearchEffect(type, value, isPercentage, customData);
    }

    // === Factory Methods for Common Effects ===

    public static ResearchEffect bankTab() {
        return new ResearchEffect(EffectType.BANK_TAB_UNLOCK, 1, false);
    }

    public static ResearchEffect bankSlots(int slots) {
        return new ResearchEffect(EffectType.BANK_SLOT_INCREASE, slots, false);
    }

    public static ResearchEffect memberCapacity(int members) {
        return new ResearchEffect(EffectType.MEMBER_CAPACITY, members, false);
    }

    public static ResearchEffect territorySize(int tiles) {
        return new ResearchEffect(EffectType.TERRITORY_SIZE, tiles, false);
    }

    public static ResearchEffect damageBonusPercent(float percent) {
        return new ResearchEffect(EffectType.COMBAT_DAMAGE_BONUS, percent, true);
    }

    public static ResearchEffect craftingSpeedPercent(float percent) {
        return new ResearchEffect(EffectType.CRAFTING_SPEED, percent, true);
    }

    public static ResearchEffect passiveIncome(int coinsPerDay) {
        return new ResearchEffect(EffectType.PASSIVE_INCOME, coinsPerDay, false);
    }
}
