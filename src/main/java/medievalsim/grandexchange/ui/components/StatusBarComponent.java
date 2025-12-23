package medievalsim.grandexchange.ui.components;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.FeedbackBus.FeedbackMessage;
import medievalsim.grandexchange.model.snapshot.BuyOrderSlotSnapshot;
import medievalsim.grandexchange.model.snapshot.SellOfferSlotSnapshot;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.controller.ControllerFocus;
import necesse.gfx.forms.controller.ControllerNavigationHandler;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;

/**
 * A status bar component that displays live trading activity.
 * 
 * Features:
 * - Dark translucent background for visual separation
 * - Left side: Latest notification with color-coded status indicator
 * - Right side: Active sell/buy order counts
 * - Heartbeat-driven updates for real-time info
 * 
 * Epic UX enhancements:
 * - Subtle pulse effect on new notifications
 * - Smart message truncation with ellipsis
 * - Color-coded status icons
 */
public class StatusBarComponent extends FormComponent {
    
    private static final FontOptions STATUS_FONT = new FontOptions(12);
    private static final int BAR_HEIGHT = 28;
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 7;
    
    // Colors
    private static final Color BAR_BACKGROUND = new Color(30, 28, 26, 200); // Dark translucent
    private static final Color BAR_BORDER = new Color(60, 55, 50);
    private static final Color STATS_TEXT = new Color(180, 175, 165);
    private static final Color SEPARATOR_COLOR = new Color(80, 75, 70);
    
    // Status indicator colors
    private static final Color STATUS_SUCCESS = new Color(90, 200, 90);
    private static final Color STATUS_ERROR = new Color(255, 140, 80);
    private static final Color STATUS_INFO = new Color(140, 190, 240);
    private static final Color STATUS_IDLE = new Color(100, 100, 100);
    
    private final Supplier<GrandExchangeViewModel> viewModelSupplier;
    private final int drawX;
    private final int drawY;
    private final int barWidth;
    
    // Cached state for rendering
    private String lastMessage = "";
    private GEFeedbackLevel lastLevel = GEFeedbackLevel.INFO;
    private long lastMessageTime = 0;
    private int cachedSellCount = 0;
    private int cachedBuyCount = 0;
    private long lastUpdateTime = 0;
    
    // Pulse animation state
    private float pulseAlpha = 0f;
    private static final long PULSE_DURATION_MS = 800;
    
    public StatusBarComponent(int x, int y, int width, Supplier<GrandExchangeViewModel> viewModelSupplier) {
        this.drawX = x;
        this.drawY = y;
        this.barWidth = width;
        this.viewModelSupplier = viewModelSupplier;
    }
    
    @Override
    public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op: status bar does not process input
    }

    @Override
    public void handleControllerEvent(ControllerEvent event, TickManager tickManager, PlayerMob perspective) {
        // No-op: controller input is ignored
    }

    @Override
    public void addNextControllerFocus(List<ControllerFocus> list,
                                        int currentXOffset,
                                        int currentYOffset,
                                        ControllerNavigationHandler customNavigationHandler,
                                        Rectangle area,
                                        boolean draw) {
        // No-op: component cannot gain focus
    }

    @Override
    public List<Rectangle> getHitboxes() {
        return Collections.emptyList();
    }
    
    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        // Update cached data periodically
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > 250) {
            updateCachedData();
            lastUpdateTime = now;
        }
        
        // Calculate pulse animation
        if (pulseAlpha > 0) {
            long elapsed = now - lastMessageTime;
            if (elapsed < PULSE_DURATION_MS) {
                pulseAlpha = 1.0f - (elapsed / (float) PULSE_DURATION_MS);
            } else {
                pulseAlpha = 0f;
            }
        }
        
        // Draw background with subtle gradient effect
        drawBackground(drawX, drawY);
        
        // Draw left side: status indicator + message
        drawStatusMessage(drawX + PADDING_X, drawY + PADDING_Y);
        
        // Draw right side: sell/buy counts
        drawActivityStats(drawX + barWidth - PADDING_X, drawY + PADDING_Y);
    }
    
    private void drawBackground(int x, int y) {
        // Main background
        Renderer.initQuadDraw(barWidth, BAR_HEIGHT)
            .color(BAR_BACKGROUND)
            .draw(x, y);
        
        // Top border line for visual separation
        Renderer.initQuadDraw(barWidth, 1)
            .color(BAR_BORDER)
            .draw(x, y);
        
        // Pulse overlay on new messages
        if (pulseAlpha > 0) {
            Color pulseColor = getStatusColor(lastLevel);
            Renderer.initQuadDraw(barWidth, BAR_HEIGHT)
                .color(new Color(
                    pulseColor.getRed(),
                    pulseColor.getGreen(),
                    pulseColor.getBlue(),
                    (int)(40 * pulseAlpha)
                ))
                .draw(x, y);
        }
    }
    
    private void drawStatusMessage(int x, int y) {
        Color statusColor = getStatusColor(lastLevel);
        FontOptions coloredFont = new FontOptions(STATUS_FONT).color(statusColor);
        
        // Draw status indicator dot
        String indicator = lastMessage.isEmpty() ? "o" : "*";
        FontManager.bit.drawString((float)x, (float)y, indicator, coloredFont);
        
        // Draw message text with smart truncation
        int messageX = x + 16;
        int maxMessageWidth = barWidth - 280; // Reserve space for stats
        
        String displayMessage = lastMessage.isEmpty() 
            ? "Ready to trade" 
            : truncateMessage(lastMessage, maxMessageWidth);
        
        FontOptions messageFont = new FontOptions(STATUS_FONT).color(lastMessage.isEmpty() ? STATUS_IDLE : statusColor);
        FontManager.bit.drawString((float)messageX, (float)y, displayMessage, messageFont);
    }
    
    private void drawActivityStats(int rightX, int y) {
        // Build stats string
        String sellStats = "Selling: " + cachedSellCount;
        String buyStats = "Buying: " + cachedBuyCount;
        String separator = " | ";
        
        // Calculate widths for right-alignment
        int sellWidth = FontManager.bit.getWidthCeil(sellStats, STATUS_FONT);
        int buyWidth = FontManager.bit.getWidthCeil(buyStats, STATUS_FONT);
        int sepWidth = FontManager.bit.getWidthCeil(separator, STATUS_FONT);
        int totalWidth = sellWidth + sepWidth + buyWidth;
        
        int currentX = rightX - totalWidth;
        
        // Draw sell count with color based on activity
        Color sellColor = cachedSellCount > 0 ? STATUS_SUCCESS : STATS_TEXT;
        FontOptions sellFont = new FontOptions(STATUS_FONT).color(sellColor);
        FontManager.bit.drawString((float)currentX, (float)y, sellStats, sellFont);
        currentX += sellWidth;
        
        // Draw separator
        FontOptions sepFont = new FontOptions(STATUS_FONT).color(SEPARATOR_COLOR);
        FontManager.bit.drawString((float)currentX, (float)y, separator, sepFont);
        currentX += sepWidth;
        
        // Draw buy count with color based on activity
        Color buyColor = cachedBuyCount > 0 ? STATUS_INFO : STATS_TEXT;
        FontOptions buyFont = new FontOptions(STATUS_FONT).color(buyColor);
        FontManager.bit.drawString((float)currentX, (float)y, buyStats, buyFont);
    }
    
    private void updateCachedData() {
        GrandExchangeViewModel vm = viewModelSupplier.get();
        if (vm == null) return;
        
        // Update notification from feedback bus
        FeedbackMessage latestMessage = findLatestMessage(vm);
        if (latestMessage != null && latestMessage.timestamp() > lastMessageTime) {
            lastMessage = latestMessage.message();
            lastLevel = latestMessage.level();
            lastMessageTime = latestMessage.timestamp();
            pulseAlpha = 1.0f; // Trigger pulse
        }
        
        // Count active sell offers
        var sellSnapshot = vm.getSellOffersSnapshot();
        if (sellSnapshot != null && sellSnapshot.slots() != null) {
            cachedSellCount = 0;
            for (SellOfferSlotSnapshot slot : sellSnapshot.slots()) {
                if (slot.isActive()) {
                    cachedSellCount++;
                }
            }
        }
        
        // Count active buy orders
        var buySnapshot = vm.getBuyOrdersSnapshot();
        if (buySnapshot != null && buySnapshot.slots() != null) {
            cachedBuyCount = 0;
            for (BuyOrderSlotSnapshot slot : buySnapshot.slots()) {
                if (slot.isOccupied() && slot.enabled() && 
                    (slot.state() == BuyOrder.BuyOrderState.ACTIVE || 
                     slot.state() == BuyOrder.BuyOrderState.PARTIAL)) {
                    cachedBuyCount++;
                }
            }
        }
    }
    
    private FeedbackMessage findLatestMessage(GrandExchangeViewModel vm) {
        FeedbackMessage latest = null;
        long latestTime = 0;
        
        // Check all channels for the most recent message
        for (GEFeedbackChannel channel : GEFeedbackChannel.values()) {
            FeedbackMessage msg = vm.getFeedbackBus().peek(channel);
            if (msg != null && msg.timestamp() > latestTime) {
                latest = msg;
                latestTime = msg.timestamp();
            }
        }
        
        return latest;
    }
    
    private Color getStatusColor(GEFeedbackLevel level) {
        if (level == null) return STATUS_IDLE;
        switch (level) {
            case ERROR: return STATUS_ERROR;
            case WARN: return STATUS_ERROR;
            case INFO: return STATUS_INFO;
            default: return STATUS_IDLE;
        }
    }
    
    private String truncateMessage(String message, int maxWidth) {
        if (message == null || message.isEmpty()) return "";
        
        int msgWidth = FontManager.bit.getWidthCeil(message, STATUS_FONT);
        if (msgWidth <= maxWidth) return message;
        
        // Binary search for optimal truncation point
        String ellipsis = "...";
        int ellipsisWidth = FontManager.bit.getWidthCeil(ellipsis, STATUS_FONT);
        int targetWidth = maxWidth - ellipsisWidth;
        
        int endIndex = message.length();
        while (endIndex > 0 && FontManager.bit.getWidthCeil(message.substring(0, endIndex), STATUS_FONT) > targetWidth) {
            endIndex--;
        }
        
        return message.substring(0, endIndex) + ellipsis;
    }
    
    @Override
    public boolean shouldSkipRenderBoxCheck() {
        // Always render regardless of bounds
        return true;
    }
}
