package medievalsim.patches;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import medievalsim.buildmode.ShapeCalculator;
import medievalsim.util.ModLogger;
import medievalsim.util.ZoneProtectionValidator;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.Packet;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketPlaceObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.multiTile.MultiTile;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target=ObjectItem.class, name="onAttack", arguments={Level.class, int.class, int.class, ItemAttackerMob.class, int.class, InventoryItem.class, ItemAttackSlot.class, int.class, int.class, GNDItemMap.class})
public class ObjectItemOnAttackPatch {
    public static final ThreadLocal<InventoryItem> buildModeResult = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> shouldSkip = new ThreadLocal<>();

    @Advice.OnMethodEnter(skipOn=Advice.OnNonDefaultValue.class)
    public static Boolean onEnter(@Advice.This ObjectItem objectItem, @Advice.Argument(value=0) Level level, @Advice.Argument(value=1) int x, @Advice.Argument(value=2) int y, @Advice.Argument(value=3) ItemAttackerMob attackerMob, @Advice.Argument(value=4) int attackHeight, @Advice.Argument(value=5, readOnly=false) InventoryItem item, @Advice.Argument(value=6) ItemAttackSlot slot, @Advice.Argument(value=7) int animAttack, @Advice.Argument(value=8) int seed, @Advice.Argument(value=9) GNDItemMap mapContent) {
        boolean hasBuildMode;
        if (!attackerMob.isPlayer) {
            return null;
        }
        PlayerMob player = (PlayerMob)attackerMob;
        
        // Check protected zone permissions for PLACING before any vanilla logic runs
        // This prevents item consumption when placement is blocked
        // NOTE: Breaking is handled by ToolDamageItemCanDamageTilePatch
        if (level.isServer() && player.getServerClient() != null) {
            ZoneProtectionValidator.ValidationResult validation = 
                ZoneProtectionValidator.validatePlacementAtPosition(level, 
                    GameMath.getTileCoordinate(x), 
                    GameMath.getTileCoordinate(y), 
                    player.getServerClient());
            if (!validation.isAllowed()) {
                String message = necesse.engine.localization.Localization.translate("message", validation.getReason());
                player.getServerClient().sendChatMessage(message);
                return true; // Skip method to prevent item consumption
            }
        }
        
        hasBuildMode = mapContent != null && mapContent.getBoolean("medievalsim_buildmode");
        if (!hasBuildMode) {
            return null;
        }
        if (!level.isServer()) {
            InventoryItem clientResult = slot.getItem();
            buildModeResult.set(clientResult);
            shouldSkip.set(true);
            return true;
        }
        try {
            int selectedShape = mapContent.getInt("medievalsim_shape");
            boolean isHollow = mapContent.getBoolean("medievalsim_isHollow");
            int lineLength = mapContent.getInt("medievalsim_lineLength");
            int squareSize = mapContent.getInt("medievalsim_squareSize");
            int circleRadius = mapContent.getInt("medievalsim_circleRadius");
            int spacing = mapContent.getInt("medievalsim_spacing");
            int direction = mapContent.getInt("medievalsim_direction");
            int playerDir = player.isAttacking ? player.beforeAttackDir : player.getDir();
            int objectRotation = ObjectItemOnAttackPatch.calculateFinalRotation(playerDir, direction);
            GameObject object = objectItem.getObject();
            if (object == null) {
                return null;
            }
            MultiTile multiTile = object.getMultiTile(objectRotation);
            int objectWidth = multiTile.width;
            int objectHeight = multiTile.height;
            int centerTileX = GameMath.getTileCoordinate((int)x);
            int centerTileY = GameMath.getTileCoordinate((int)y);
            List<Point> positions = ShapeCalculator.calculatePositions(centerTileX, centerTileY, selectedShape, isHollow, playerDir, lineLength, squareSize, circleRadius, spacing, objectWidth, objectHeight);
            if (!level.isClient()) {
                PermissionLevel permLevel;
                ServerClient serverClient = player.getServerClient();
                if (serverClient != null && ((permLevel = serverClient.getPermissionLevel()) == null || permLevel.getLevel() < PermissionLevel.ADMIN.getLevel())) {
                    ModLogger.warn("Server rejected build mode placement: Player %s does not have ADMIN permission", player.getDisplayName());
                    serverClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "buildmode.nopermission"));
                    buildModeResult.set(item);
                    shouldSkip.set(true);
                    return true;
                }
                if (positions.size() > 500) {
                    ModLogger.warn("Server rejected placement: %d blocks exceeds limit of 500 from player %s", positions.size(), player.getDisplayName());
                    if (serverClient != null) {
                        serverClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "buildmode.blocklimit", "limit", 500));
                    }
                    buildModeResult.set(item);
                    shouldSkip.set(true);
                    return true;
                }
            }
            if (positions.size() > 500) {
                ModLogger.warn("Attempted to place %d objects, limiting to 500", positions.size());
                positions = positions.subList(0, 500);
            }
            int availableItems = item.getAmount();
            ArrayList<Point> validPositions = new ArrayList<Point>();
            for (Point point : positions) {
                if (validPositions.size() >= availableItems) break;
                String canPlaceResult = object.canPlace(level, 0, point.x, point.y, objectRotation, true, false);
                if (!level.isTileWithinBounds(point.x, point.y) || level.isProtected(point.x, point.y) || canPlaceResult != null) continue;
                validPositions.add(point);
            }
            if (validPositions.isEmpty()) {
                return null;
            }
            int placedCount = 0;
            for (Point point : validPositions) {
                object.placeObject(level, 0, point.x, point.y, objectRotation, true);
                if (level.isServer()) {
                    level.getServer().network.sendToClientsWithTile((Packet)new PacketPlaceObject(level, player.getServerClient(), 0, point.x, point.y, object.getID(), objectRotation, true, false), level, point.x, point.y);
                }
                level.onObjectPlaced(object, 0, point.x, point.y, player.getServerClient());
                ++placedCount;
            }
            if (placedCount > 0) {
                item.setAmount(item.getAmount() - placedCount);
                if (player.getServerClient() != null) {
                    player.getServerClient().newStats.objects_placed.increment(placedCount);
                }
            }
            player.currentAttackLastPlacePosition = new Point(x, y);
            InventoryItem inventoryItem = item.getAmount() <= 0 ? null : item;
            buildModeResult.set(inventoryItem);
            shouldSkip.set(true);
            return true;
        }
        catch (IllegalArgumentException e) {
            ModLogger.error("Invalid build mode parameters", e);
            return null;
        }
        catch (Exception e) {
            ModLogger.error("Unexpected error in build mode object placement", e);
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Advice.OnMethodExit(onThrowable=Throwable.class)
    public static void onExit(@Advice.This ObjectItem objectItem, @Advice.Argument(value=0) Level level, @Advice.Argument(value=6) ItemAttackSlot slot, @Advice.Return(readOnly=false) InventoryItem returnValue) {
        try {
            Boolean skip = shouldSkip.get();
            if (skip != null && skip.booleanValue()) {
                returnValue = buildModeResult.get();
            }
        }
        finally {
            buildModeResult.remove();
            shouldSkip.remove();
        }
    }

    public static int calculateFinalRotation(int playerDir, int relativeDir) {
        switch (relativeDir) {
            case 0: {
                return playerDir;
            }
            case 1: {
                return (playerDir + 2) % 4;
            }
            case 2: {
                return (playerDir + 3) % 4;
            }
            case 3: {
                return (playerDir + 1) % 4;
            }
        }
        return playerDir;
    }
}

