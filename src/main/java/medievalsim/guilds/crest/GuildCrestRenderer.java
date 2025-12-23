/*
 * Guild Crest Renderer for Medieval Sim Mod
 * Renders guild crests on UI and objects.
 * 
 * Uses textures from resources/items/gs_* (gs_shape, gs_emblem, gs_border) and item masks
 */
package medievalsim.guilds.crest;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.util.ModLogger;
import necesse.gfx.gameTexture.GameTexture;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders guild crests from design data.
 * 
 * Texture files used:
 * - Background shapes: items/gs_shape_shield, _circle, _diamond, _square, _banner
 * - Borders: items/gs_border_none, _simple, _ornate, _royal
 * - Emblems: items/gs_emblem_sword, _pickaxe, _tree, etc.
 */
public class GuildCrestRenderer {

    // Texture caches
    private static Map<String, GameTexture> backgroundTextures = new HashMap<>();
    private static Map<String, GameTexture> emblemTextures = new HashMap<>();
    private static Map<String, GameTexture> borderTextures = new HashMap<>();
    // Optional mask textures for fill areas (artist-provided pale layer)
    private static Map<String, GameTexture> maskTextures = new HashMap<>();

    // Optional background preview used to simulate a flag or item background
    private static GameTexture crestBackgroundTexture = null;

    // Background shape texture names (matching files)
    private static final String[] BG_TEXTURE_NAMES = {
        "gs_shape_shield",
        "gs_shape_circle", 
        "gs_shape_banner",   // Note: banner may not exist, use shield as fallback
        "gs_shape_diamond",
        "gs_shape_square"
    };
    // Border style texture names
    private static final String[] BORDER_TEXTURE_NAMES = {
        "gs_border_none",
        "gs_border_simple",
        "gs_border_ornate",
        "gs_border_royal"
    };

    // Emblem texture names (matching gs_emblem_*.png files)
    private static final String[] EMBLEM_TEXTURE_NAMES = {
        "gs_emblem_sword",
        "gs_emblem_pickaxe",
        "gs_emblem_tree",
        "gs_emblem_crown",
        "gs_emblem_star",
        "gs_emblem_hammer",
        "gs_emblem_anvil",
        "gs_emblem_coin",
        "gs_emblem_shield",
        "gs_emblem_axe",
        "gs_emblem_wheat",
        "gs_emblem_diamond",
        "gs_emblem_castle",
        "gs_emblem_dragon",
        "gs_emblem_wolf",
        "gs_emblem_bear",
        "gs_emblem_wolf",
        "gs_emblem_bear",
        "gs_emblem_skull",
        "gs_emblem_rose"
    };

    // Texture loading status
    private static boolean texturesLoaded = false;

    /**
     * Load crest textures.
     */
    public static void loadTextures() {
        if (texturesLoaded) return;

        ModLogger.info("Loading guild crest textures...");

        // Load background shapes
        for (String name : BG_TEXTURE_NAMES) {
            try {
                GameTexture tex = GameTexture.fromFile("items/" + name);
                if (tex != null) {
                    backgroundTextures.put(name, tex);
                }
            } catch (Exception e) {
                ModLogger.debug("Could not load background texture: " + name);
            }
        }

        // Load borders
        for (String name : BORDER_TEXTURE_NAMES) {
            try {
                GameTexture tex = GameTexture.fromFile("items/" + name);
                if (tex != null) {
                    borderTextures.put(name, tex);
                }
            } catch (Exception e) {
                ModLogger.debug("Could not load border texture: " + name);
            }
        }

        // Load emblems
        for (String name : EMBLEM_TEXTURE_NAMES) {
            if (!emblemTextures.containsKey(name)) {
                try {
                    GameTexture tex = GameTexture.fromFile("items/" + name);
                    if (tex != null) {
                        emblemTextures.put(name, tex);
                    }
                } catch (Exception e) {
                    ModLogger.debug("Could not load emblem texture: " + name);
                }
            }
        }

        ModLogger.info("Loaded %d backgrounds, %d borders, %d emblems", 
            backgroundTextures.size(), borderTextures.size(), emblemTextures.size());

        // Attempt to load optional mask textures for background shapes (for artist-provided pale fill layers)
        for (String name : BG_TEXTURE_NAMES) {
            try {
                GameTexture mask = GameTexture.fromFile("items/" + name + "_mask");
                if (mask != null) {
                    maskTextures.put(name, mask);
                }
            } catch (Exception e) {
                // missing mask for this shape is fine
            }
        }

        // Load optional crest background texture for mockups; prefer new item-based file then fallback to ui file
        try {
            GameTexture bg = GameTexture.fromFile("items/guild_background");
            if (bg == null) {
                bg = GameTexture.fromFile("ui/guild_crest_background");
            }
            if (bg != null) {
                crestBackgroundTexture = bg;
                ModLogger.info("Loaded crest background texture");
            }
        } catch (Exception e) {
            ModLogger.debug("No crest background texture found: items/guild_background or ui/guild_crest_background");
        }

        texturesLoaded = true;
    }

    /**
     * Get the background texture for a shape index.
     */
    public static GameTexture getBackgroundTexture(int shapeIndex) {
        if (shapeIndex >= 0 && shapeIndex < BG_TEXTURE_NAMES.length) {
            return backgroundTextures.get(BG_TEXTURE_NAMES[shapeIndex]);
        }
        return backgroundTextures.get(BG_TEXTURE_NAMES[0]); // Default to shield
    }

    /**
     * Get the border texture for a style index and shape index.
     * Tries a shape-specific texture (e.g. guildcrest_border_simple_shield) first,
     * then falls back to the generic border (e.g. guildcrest_border_simple).
     */
    public static GameTexture getBorderTexture(int styleIndex, int shapeIndex) {
        if (styleIndex >= 0 && styleIndex < BORDER_TEXTURE_NAMES.length) {
            String baseName = BORDER_TEXTURE_NAMES[styleIndex];
            String shapeSuffix = BG_TEXTURE_NAMES[Math.max(0, Math.min(shapeIndex, BG_TEXTURE_NAMES.length - 1))];
            String shapedName = baseName + "_" + shapeSuffix.replace("gs_shape_", "");
            // Try shaped border (e.g. guildcrest_border_simple_shield)
            if (borderTextures.containsKey(shapedName)) {
                return borderTextures.get(shapedName);
            }
            // Fallback to generic border
            if (borderTextures.containsKey(baseName)) {
                return borderTextures.get(baseName);
            }
        }
        return borderTextures.get(BORDER_TEXTURE_NAMES[0]); // Default to none
    }

    /**
     * Get the emblem texture for an emblem ID.
     */
    public static GameTexture getEmblemTexture(int emblemID) {
        if (emblemID >= 0 && emblemID < EMBLEM_TEXTURE_NAMES.length) {
            return emblemTextures.get(EMBLEM_TEXTURE_NAMES[emblemID]);
        }
        return emblemTextures.get(EMBLEM_TEXTURE_NAMES[0]); // Default to sword
    }

    /**
     * Draw a crest at the specified UI location.
     * @param design The crest design to render
     * @param x Screen X position
     * @param y Screen Y position
     * @param size Size to draw (width and height)
     */
    public static void drawCrest(GuildSymbolDesign design, int x, int y, int size) {
        if (design == null) return;
        
        // Optionally draw a background mockup (e.g., a flag preview) if available
        if (crestBackgroundTexture != null) {
            try {
                crestBackgroundTexture.initDraw()
                    .color(new Color(design.getBackgroundColor()))
                    .size(size, size)
                    .draw(x, y);
            } catch (Exception ignored) {}
        }

        // Draw background shape with the SHAPE (primary) color tint
        String bgName = BG_TEXTURE_NAMES[Math.max(0, Math.min(design.getBackgroundShape(), BG_TEXTURE_NAMES.length-1))];
        GameTexture bgTex = getBackgroundTexture(design.getBackgroundShape());
        if (bgTex != null) {
            Color shapeColor = new Color(design.getPrimaryColor());
            bgTex.initDraw()
                .color(shapeColor)
                .size(size, size)
                .draw(x, y);
        }

        // If the artist provided a mask/pale layer for this shape, draw it as a masked fill using the design's background color (fill color)
        try {
            GameTexture mask = maskTextures.get(bgName);
            if (mask != null) {
                Color fill = new Color(design.getBackgroundColor());
                mask.initDraw()
                    .color(fill)
                    .size(size, size)
                    .draw(x, y);
            }
        } catch (Exception ignored) {}

        // Draw emblem with emblem color tint
        GameTexture emblemTex = getEmblemTexture(design.getEmblemID());
        if (emblemTex != null) {
            Color emblemColor = new Color(design.getEmblemColor());
            int emblemSize = (int)(size * 0.6);
            int emblemOffset = (size - emblemSize) / 2;
            emblemTex.initDraw()
                .color(emblemColor)
                .size(emblemSize, emblemSize)
                .draw(x + emblemOffset, y + emblemOffset);
        }

        // Draw border (shape-aware)
        GameTexture borderTex = getBorderTexture(design.getBorderStyle(), design.getBackgroundShape());
        if (borderTex != null && design.getBorderStyle() > 0) {
            Color secondaryColor = new Color(design.getSecondaryColor());
            borderTex.initDraw()
                .color(secondaryColor)
                .size(size, size)
                .draw(x, y);
        }
    }

    /**
     * Draw crest on the game world (for objects like flags).
     * @param design The crest design
     * @param x World draw X position
     * @param y World draw Y position  
     * @param size Size in pixels
     */
    public static void drawCrestWorld(GuildSymbolDesign design, float x, float y, float size) {
        // World rendering uses same approach but with float positions
        drawCrest(design, (int)x, (int)y, (int)size);
    }

    /**
     * Draw the symbol onto an item mask (used for item previews such as crest item, banners, flags).
     * @param design The crest/symbol design
     * @param itemBaseName The base item name (e.g., "guildcrest", "guildbanner", "guildflag")
     * @param x Screen X position
     * @param y Screen Y position
     * @param size Size to draw (width and height)
     */
    public static void drawSymbolOnItem(GuildSymbolDesign design, String itemBaseName, int x, int y, int size) {
        if (design == null || itemBaseName == null) return;

        // Draw the base item sprite first (so the item artwork is visible behind the symbol)
        try {
            GameTexture itemTex = GameTexture.fromFile("items/" + itemBaseName);
            if (itemTex != null) {
                itemTex.initDraw()
                    .size(size, size)
                    .draw(x, y);
            }
        } catch (Exception ignored) {}

        // Try to load an item-specific mask first (e.g., items/<base>_mask)
        GameTexture mask = null;
        try {
            mask = GameTexture.fromFile("items/" + itemBaseName + "_mask");
        } catch (Exception ignored) {}

        // If no mask found, fallback to a crest-style preview centered in the area
        if (mask == null) {
            drawCrest(design, x, y, size);
            return;
        }

        // Composite approach: render symbol to temp texture, apply mask alpha, then draw result
        // This ensures the symbol only appears where the mask is opaque (clipped to mask shape)
        try {
            int maskW = mask.getWidth();
            int maskH = mask.getHeight();

            // Create a temporary texture for compositing the symbol
            GameTexture composite = new GameTexture("symbolComposite", maskW, maskH);
            
            // Fill composite with background color where mask is opaque
            Color fillColor = new Color(design.getBackgroundColor());
            for (int mx = 0; mx < maskW; mx++) {
                for (int my = 0; my < maskH; my++) {
                    int maskAlpha = mask.getAlpha(mx, my);
                    if (maskAlpha > 0) {
                        composite.setPixel(mx, my, fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), maskAlpha);
                    }
                }
            }

            // Find content bounds for scaling symbol layers
            int left = 0, right = maskW - 1, top = 0, bottom = maskH - 1;
            boolean foundLeft = false, foundRight = false, foundTop = false, foundBottom = false;
            
            for (int mx = 0; mx < maskW && !foundLeft; mx++) {
                for (int my = 0; my < maskH; my++) {
                    if (mask.getAlpha(mx, my) > 0) { left = mx; foundLeft = true; break; }
                }
            }
            for (int mx = maskW - 1; mx >= 0 && !foundRight; mx--) {
                for (int my = 0; my < maskH; my++) {
                    if (mask.getAlpha(mx, my) > 0) { right = mx; foundRight = true; break; }
                }
            }
            for (int my = 0; my < maskH && !foundTop; my++) {
                for (int mx = 0; mx < maskW; mx++) {
                    if (mask.getAlpha(mx, my) > 0) { top = my; foundTop = true; break; }
                }
            }
            for (int my = maskH - 1; my >= 0 && !foundBottom; my--) {
                for (int mx = 0; mx < maskW; mx++) {
                    if (mask.getAlpha(mx, my) > 0) { bottom = my; foundBottom = true; break; }
                }
            }

            int contentW = Math.max(right - left + 1, 1);
            int contentH = Math.max(bottom - top + 1, 1);

            // Render symbol layers to temp textures then composite them with mask alpha
            GameTexture shapeTex = getBackgroundTexture(design.getBackgroundShape());
            if (shapeTex != null) {
                GameTexture scaledShape = shapeTex.resize(contentW, contentH);
                Color shapeColor = new Color(design.getPrimaryColor());
                for (int sx = 0; sx < contentW; sx++) {
                    for (int sy = 0; sy < contentH; sy++) {
                        int destX = left + sx;
                        int destY = top + sy;
                        if (destX < maskW && destY < maskH) {
                            int maskAlpha = mask.getAlpha(destX, destY);
                            if (maskAlpha > 0) {
                                Color pixel = scaledShape.getColor(sx, sy);
                                int pixelAlpha = pixel.getAlpha();
                                if (pixelAlpha > 0) {
                                    // Only tint opaque pixels; for semi-transparent pixels, use the pixel's own color
                                    int r, g, b;
                                    if (pixelAlpha > 250) {
                                        // Fully opaque - apply tint
                                        r = (pixel.getRed() * shapeColor.getRed()) / 255;
                                        g = (pixel.getGreen() * shapeColor.getGreen()) / 255;
                                        b = (pixel.getBlue() * shapeColor.getBlue()) / 255;
                                    } else {
                                        // Semi-transparent - preserve original color to avoid color bleeding
                                        r = pixel.getRed();
                                        g = pixel.getGreen();
                                        b = pixel.getBlue();
                                    }
                                    int a = (pixelAlpha * maskAlpha) / 255;
                                    // Blend with existing pixel (over operator)
                                    Color existing = composite.getColor(destX, destY);
                                    int finalR = r + ((existing.getRed() * (255 - a)) / 255);
                                    int finalG = g + ((existing.getGreen() * (255 - a)) / 255);
                                    int finalB = b + ((existing.getBlue() * (255 - a)) / 255);
                                    int finalA = a + ((existing.getAlpha() * (255 - a)) / 255);
                                    composite.setPixel(destX, destY, finalR, finalG, finalB, finalA);
                                }
                            }
                        }
                    }
                }
            }

            // Emblem layer
            GameTexture emblemTex = getEmblemTexture(design.getEmblemID());
            if (emblemTex != null) {
                float emblemScale = 0.6f;
                int emblemSize = (int)(Math.min(contentW, contentH) * emblemScale);
                GameTexture scaledEmblem = emblemTex.resize(emblemSize, emblemSize);
                Color emblemColor = new Color(design.getEmblemColor());
                int emblemOffsetX = left + (contentW - emblemSize) / 2;
                int emblemOffsetY = top + (contentH - emblemSize) / 2;
                
                for (int ex = 0; ex < emblemSize; ex++) {
                    for (int ey = 0; ey < emblemSize; ey++) {
                        int destX = emblemOffsetX + ex;
                        int destY = emblemOffsetY + ey;
                        if (destX >= 0 && destX < maskW && destY >= 0 && destY < maskH) {
                            int maskAlpha = mask.getAlpha(destX, destY);
                            if (maskAlpha > 0) {
                                Color pixel = scaledEmblem.getColor(ex, ey);
                                int pixelAlpha = pixel.getAlpha();
                                if (pixelAlpha > 0) {
                                    int r, g, b;
                                    if (pixelAlpha > 250) {
                                        r = (pixel.getRed() * emblemColor.getRed()) / 255;
                                        g = (pixel.getGreen() * emblemColor.getGreen()) / 255;
                                        b = (pixel.getBlue() * emblemColor.getBlue()) / 255;
                                    } else {
                                        r = pixel.getRed();
                                        g = pixel.getGreen();
                                        b = pixel.getBlue();
                                    }
                                    int a = (pixelAlpha * maskAlpha) / 255;
                                    Color existing = composite.getColor(destX, destY);
                                    int finalR = r + ((existing.getRed() * (255 - a)) / 255);
                                    int finalG = g + ((existing.getGreen() * (255 - a)) / 255);
                                    int finalB = b + ((existing.getBlue() * (255 - a)) / 255);
                                    int finalA = a + ((existing.getAlpha() * (255 - a)) / 255);
                                    composite.setPixel(destX, destY, finalR, finalG, finalB, finalA);
                                }
                            }
                        }
                    }
                }
            }

            // Border layer
            GameTexture borderTex = getBorderTexture(design.getBorderStyle(), design.getBackgroundShape());
            if (borderTex != null && design.getBorderStyle() > 0) {
                GameTexture scaledBorder = borderTex.resize(contentW, contentH);
                Color borderColor = new Color(design.getSecondaryColor());
                for (int bx = 0; bx < contentW; bx++) {
                    for (int by = 0; by < contentH; by++) {
                        int destX = left + bx;
                        int destY = top + by;
                        if (destX < maskW && destY < maskH) {
                            int maskAlpha = mask.getAlpha(destX, destY);
                            if (maskAlpha > 0) {
                                Color pixel = scaledBorder.getColor(bx, by);
                                int pixelAlpha = pixel.getAlpha();
                                if (pixelAlpha > 0) {
                                    int r, g, b;
                                    if (pixelAlpha > 250) {
                                        r = (pixel.getRed() * borderColor.getRed()) / 255;
                                        g = (pixel.getGreen() * borderColor.getGreen()) / 255;
                                        b = (pixel.getBlue() * borderColor.getBlue()) / 255;
                                    } else {
                                        r = pixel.getRed();
                                        g = pixel.getGreen();
                                        b = pixel.getBlue();
                                    }
                                    int a = (pixelAlpha * maskAlpha) / 255;
                                    Color existing = composite.getColor(destX, destY);
                                    int finalR = r + ((existing.getRed() * (255 - a)) / 255);
                                    int finalG = g + ((existing.getGreen() * (255 - a)) / 255);
                                    int finalB = b + ((existing.getBlue() * (255 - a)) / 255);
                                    int finalA = a + ((existing.getAlpha() * (255 - a)) / 255);
                                    composite.setPixel(destX, destY, finalR, finalG, finalB, finalA);
                                }
                            }
                        }
                    }
                }
            }

            // Reset texture so it uploads to GPU
            composite.resetTexture();
            
            // Draw the composited symbol (now properly masked)
            composite.initDraw()
                .size(size, size)
                .draw(x, y);
                
        } catch (Exception e) {
            ModLogger.debug("Symbol compositing failed: " + e.getMessage());
        }
    }

    /**
     * Check if textures are loaded.
     */
    public static boolean areTexturesLoaded() {
        return texturesLoaded;
    }

    /**
     * Get count of loaded textures for debugging.
     */
    public static int getLoadedTextureCount() {
        return backgroundTextures.size() + borderTextures.size() + emblemTextures.size() + maskTextures.size();
    }
}
