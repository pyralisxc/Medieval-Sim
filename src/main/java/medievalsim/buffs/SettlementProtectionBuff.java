package medievalsim.buffs;

import necesse.engine.localization.Localization;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.staticBuffs.Buff;
import necesse.gfx.gameTooltips.ListGameTooltips;

/**
 * Cosmetic buff that indicates the player is currently inside a
 * Settlement with protection enabled. Shows settlement name, owner,
 * and current permissions dynamically based on player's role.
 */
public class SettlementProtectionBuff extends Buff {

    public SettlementProtectionBuff() {
        this.isImportant = false;
        this.canCancel = false;
        this.isVisible = true;
        this.isPassive = true;  // Mark as passive so it doesn't expire like equipment buffs
    }

    @Override
    public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
        // No special init required for cosmetic indicator
    }

    @Override
    public ListGameTooltips getTooltip(ActiveBuff buff, GameBlackboard blackboard) {
        ListGameTooltips tooltips = new ListGameTooltips();
        
        // Read settlement info from GND data (set by server when buff was applied)
        String settlementName = buff.getGndData().getString("settlementName");
        String ownerName = buff.getGndData().getString("ownerName");
        
        if (settlementName != null && !settlementName.isEmpty()) {
            // Settlement title
            tooltips.add("§eSettlement Protection: §f" + settlementName);
            
            // Owner info
            if (ownerName != null && !ownerName.isEmpty()) {
                tooltips.add("§7Owner: §f" + ownerName);
            }
            
            // Check if player has elevated access
            boolean isElevated = buff.getGndData().getBoolean("isElevated");
            
            if (isElevated) {
                tooltips.add("§aFull Access (Owner/Team)");
                tooltips.add("§7Can build, break, and interact");
            } else {
                // Read individual permissions from GND data
                boolean canPlace = buff.getGndData().getBoolean("canPlace");
                boolean canBreak = buff.getGndData().getBoolean("canBreak");
                boolean canDoors = buff.getGndData().getBoolean("canDoors");
                boolean canChests = buff.getGndData().getBoolean("canChests");
                boolean canStations = buff.getGndData().getBoolean("canStations");
                boolean canSwitches = buff.getGndData().getBoolean("canSwitches");
                boolean canFurniture = buff.getGndData().getBoolean("canFurniture");
                
                // Build allowed/blocked lists
                StringBuilder allowed = new StringBuilder("§aAllowed: §f");
                StringBuilder blocked = new StringBuilder("§cBlocked: §f");
                
                boolean hasAny = false;
                if (canPlace) { allowed.append("Place, "); hasAny = true; } else { blocked.append("Place, "); }
                if (canBreak) { allowed.append("Break, "); hasAny = true; } else { blocked.append("Break, "); }
                if (canDoors) { allowed.append("Doors, "); hasAny = true; } else { blocked.append("Doors, "); }
                if (canChests) { allowed.append("Chests, "); hasAny = true; } else { blocked.append("Chests, "); }
                if (canStations) { allowed.append("Stations, "); hasAny = true; } else { blocked.append("Stations, "); }
                if (canSwitches) { allowed.append("Switches, "); hasAny = true; } else { blocked.append("Switches, "); }
                if (canFurniture) { allowed.append("Furniture, "); hasAny = true; } else { blocked.append("Furniture, "); }
                
                if (!hasAny) {
                    tooltips.add("§cNo permissions in this settlement");
                } else {
                    String allowedStr = allowed.toString();
                    if (allowedStr.endsWith(", ")) allowedStr = allowedStr.substring(0, allowedStr.length() - 2);
                    tooltips.add(allowedStr);
                    
                    String blockedStr = blocked.toString();
                    if (blockedStr.endsWith(", ")) blockedStr = blockedStr.substring(0, blockedStr.length() - 2);
                    tooltips.add(blockedStr);
                }
            }
            
            return tooltips;
        }
        
        // Fallback if GND data not available
        tooltips.add(Localization.translate("buff", "settlementprotection"));
        tooltips.add(Localization.translate("buff", "settlementprotectiondesc"));
        return tooltips;
    }
}

