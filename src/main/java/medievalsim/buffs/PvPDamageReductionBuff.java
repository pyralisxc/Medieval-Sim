/*
 * PvP damage reduction buff - reduces incoming PvP damage
 * Used for temporary damage mitigation in PvP zones
 *  necesse.engine.util.GameBlackboard
 *  necesse.entity.mobs.buffs.ActiveBuff
 *  necesse.entity.mobs.buffs.BuffEventSubscriber
 *  necesse.entity.mobs.buffs.staticBuffs.Buff
 *  necesse.gfx.gameTexture.GameTexture
 *  necesse.gfx.gameTooltips.ListGameTooltips
 */
package medievalsim.buffs;

import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.gfx.GameColor;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;

public class PvPDamageReductionBuff
extends Buff {
    public PvPDamageReductionBuff() {
        this.isImportant = false;
        this.canCancel = false;
        this.isVisible = true;
        this.isPassive = true;  // Mark as passive so it doesn't show duration/expire
    }

    public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
    }

    public void loadTextures() {
        this.iconTexture = GameTexture.fromFile((String)"buffs/pvpdamagereduction.png");
    }

    public ListGameTooltips getTooltip(ActiveBuff buff, GameBlackboard blackboard) {
        ListGameTooltips tooltips = new ListGameTooltips();
        
        // Get zone name from buff data
        String zoneName = buff.getGndData().getString("zoneName");
        if (zoneName != null && !zoneName.isEmpty()) {
            tooltips.add(GameColor.YELLOW.getColorCode() + "PvP Zone: " + GameColor.WHITE.getColorCode() + zoneName);
        } else {
            tooltips.add(GameColor.YELLOW.getColorCode() + "PvP Zone");
        }
        
        // Get damage multiplier and format percentage
        float damageMultiplier = buff.getGndData().getFloat("damageMultiplier", 1.0f);
        String damagePercent = medievalsim.zones.domain.PvPZone.formatDamagePercent(damageMultiplier);
        tooltips.add(GameColor.RED.getColorCode() + "PvP Damage: " + GameColor.WHITE.getColorCode() + damagePercent);
        
        return tooltips;
    }
}

