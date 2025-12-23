package medievalsim.zones.ui;

import medievalsim.util.Constants;
import medievalsim.zones.domain.GuildZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.domain.AdminZone;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.util.Zoning;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;
import necesse.level.maps.hudManager.HudDrawElement;

public class ZoneVisualizationHud
extends HudDrawElement {
    private final Supplier<Map<Integer, ProtectedZone>> protectedZonesSupplier;
    private final Supplier<Map<Integer, PvPZone>> pvpZonesSupplier;
    private final Supplier<Map<Integer, GuildZone>> guildZonesSupplier;
    private final boolean showProtectedZones;
    private final boolean showPvPZones;
    private final boolean showGuildZones;

    /**
     * Original constructor for protected and PvP zones.
     */
    public ZoneVisualizationHud(Supplier<Map<Integer, ProtectedZone>> protectedZonesSupplier, Supplier<Map<Integer, PvPZone>> pvpZonesSupplier, boolean showProtectedZones, boolean showPvPZones) {
        this(protectedZonesSupplier, pvpZonesSupplier, showProtectedZones, showPvPZones, Collections::emptyMap);
    }

    /**
     * Extended constructor supporting guild zones.
     */
    public ZoneVisualizationHud(Supplier<Map<Integer, ProtectedZone>> protectedZonesSupplier, Supplier<Map<Integer, PvPZone>> pvpZonesSupplier, boolean showProtectedZones, boolean showPvPZones, Supplier<Map<Integer, GuildZone>> guildZonesSupplier) {
        this.protectedZonesSupplier = protectedZonesSupplier;
        this.pvpZonesSupplier = pvpZonesSupplier;
        this.guildZonesSupplier = guildZonesSupplier != null ? guildZonesSupplier : Collections::emptyMap;
        this.showProtectedZones = showProtectedZones;
        this.showPvPZones = showPvPZones;
        // Show guild zones if a non-empty supplier is provided (determined by checking if we're not using a default empty supplier)
        this.showGuildZones = (guildZonesSupplier != null);
    }

    public void addDrawables(List<SortedDrawable> list, GameCamera camera, PlayerMob perspective) {
        Map<Integer, PvPZone> pvpZones;
        Map<Integer, ProtectedZone> protectedZones;
        Map<Integer, GuildZone> guildZones;
        if (this.showProtectedZones && this.protectedZonesSupplier != null && (protectedZones = this.protectedZonesSupplier.get()) != null) {
            for (ProtectedZone protectedZone : protectedZones.values()) {
                this.drawZone(protectedZone, list, camera);
            }
        }
        if (this.showPvPZones && this.pvpZonesSupplier != null && (pvpZones = this.pvpZonesSupplier.get()) != null) {
            for (PvPZone pvPZone : pvpZones.values()) {
                this.drawZone(pvPZone, list, camera);
            }
        }
        if (this.showGuildZones && this.guildZonesSupplier != null && (guildZones = this.guildZonesSupplier.get()) != null) {
            for (GuildZone guildZone : guildZones.values()) {
                this.drawZone(guildZone, list, camera);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void drawZone(AdminZone zone, List<SortedDrawable> list, final GameCamera camera) {
        if (zone == null || zone.zoning == null) {
            return;
        }
        Color edgeColor = Color.getHSBColor(
            (float)zone.colorHue / Constants.ZoneVisualization.HUE_CONVERSION_FACTOR, 
            Constants.ZoneVisualization.COLOR_SATURATION, 
            1.0f);
        Color fillColor = new Color(
            edgeColor.getRed(), 
            edgeColor.getGreen(), 
            edgeColor.getBlue(), 
            Constants.ZoneVisualization.FILL_ALPHA);
        Zoning zoning = zone.zoning;
        synchronized (zoning) {
            Rectangle bounds;
            final SharedTextureDrawOptions options = zone.zoning.getDrawOptions(edgeColor, fillColor, camera);
            if (options != null) {
                list.add(new SortedDrawable(){

                    public int getPriority() {
                        return Constants.ZoneVisualization.ZONE_OVERLAY_PRIORITY;
                    }

                    public void draw(TickManager tickManager) {
                        options.draw();
                    }
                });
            }
            if ((bounds = zone.zoning.getTileBounds()) != null && !bounds.isEmpty()) {
                final int centerTileX = bounds.x + bounds.width / 2;
                final int centerTileY = bounds.y + bounds.height / 2;
                // Trim whitespace and check if empty
                final String zoneName = (zone.name == null || zone.name.trim().isEmpty())
                    ? "Unnamed Zone"
                    : zone.name.trim();
                final Color labelColor = edgeColor;
                list.add(new SortedDrawable(){

                    public int getPriority() {
                        return Constants.ZoneVisualization.ZONE_LABEL_PRIORITY;
                    }

                    public void draw(TickManager tickManager) {
                        int drawX = camera.getTileDrawX(centerTileX);
                        int drawY = camera.getTileDrawY(centerTileY);
                        FontOptions fontOptions = new FontOptions(Constants.ZoneVisualization.LABEL_FONT_SIZE)
                            .outline()
                            .color(labelColor);
                        int textWidth = FontManager.bit.getWidthCeil(zoneName, fontOptions);
                        FontManager.bit.drawString(
                            (float)(drawX - textWidth / 2), 
                            (float)(drawY + Constants.ZoneVisualization.LABEL_Y_OFFSET), 
                            zoneName, 
                            fontOptions);
                    }
                });
            }
        }
    }
}

