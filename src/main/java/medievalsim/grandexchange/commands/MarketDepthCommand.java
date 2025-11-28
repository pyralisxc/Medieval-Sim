package medievalsim.grandexchange.commands;

import medievalsim.commandcenter.domain.CommandCategory;
import medievalsim.commandcenter.service.AdminCommand;
import medievalsim.commandcenter.service.CommandResult;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.services.OrderBook;
import medievalsim.util.ModLogger;

import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Admin command to view market depth for an item.
 * Shows top buy orders and sell offers with quantities.
 * 
 * Usage: /gemarket depth <itemID>
 * Example: /gemarket depth woodlog
 */
public class MarketDepthCommand extends AdminCommand {
    
    public MarketDepthCommand() {
        super(new Builder("gemarketdepth", "GE Market Depth")
            .description("View order book market depth for an item")
            .permission(PermissionLevel.ADMIN)
            .category(CommandCategory.OTHER)
        );
    }
    
    @Override
    public CommandResult execute(Client client, Server server, ServerClient executor, Object[] args) {
        // Enforce permission at runtime as an extra safeguard
        if (!hasPermission(executor)) {
            if (executor != null) {
                executor.sendChatMessage("[GE] Permission denied: insufficient privileges");
            }
            ModLogger.warn("Unauthorized admin command attempt: %s by permission=%s",
                this.getId(), executor == null ? "unknown" : String.valueOf(executor.getPermissionLevel()));
            return CommandResult.error("Permission denied");
        }
        // Validate arguments
        if (args.length < 1) {
            return CommandResult.error("Usage: /gemarketdepth <itemID>");
        }
        
        String itemID = args[0].toString();
        
        // Get Grand Exchange data
        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(server.world.getLevel(executor));
        if (geData == null) {
            return CommandResult.error("Grand Exchange not available in this world");
        }
        
        // Get OrderBook for this specific item
        java.util.Map<String, OrderBook> orderBooks = geData.getOrderBooksByItem();
        OrderBook orderBook = orderBooks.get(itemID);
        if (orderBook == null) {
            return CommandResult.error("No active market for item: " + itemID);
        }
        
        // Get market depth
        OrderBook.MarketDepth depth = orderBook.getMarketDepth();
        
        // Build formatted output
        StringBuilder output = new StringBuilder();
        output.append(String.format("§6=== Market Depth: %s ===\n", orderBook.getItemStringID()));
        output.append(String.format("§eActive: §f%d buy orders, %d sell offers\n\n", 
            orderBook.getBuyOrderCount(), orderBook.getSellOfferCount()));
        
        // Get buy depth (Map<Price, Quantity>)
        java.util.Map<Integer, Integer> buyDepth = depth.getBuyDepth();
        java.util.Map<Integer, Integer> sellDepth = depth.getSellDepth();
        
        // Show top 10 buy orders (highest price first - already sorted DESC)
        output.append("§2=== BUY ORDERS (Best Bids) ===\n");
        if (buyDepth.isEmpty()) {
            output.append("§7No buy orders\n");
        } else {
            int count = 0;
            for (java.util.Map.Entry<Integer, Integer> entry : buyDepth.entrySet()) {
                if (count >= 10) break;
                int price = entry.getKey();
                int qty = entry.getValue();
                output.append(String.format("§f%d. §a%d coins §f× §e%d qty §7(Total: %d coins)\n",
                    count + 1, price, qty, price * qty
                ));
                count++;
            }
        }
        
        output.append("\n");
        
        // Show top 10 sell offers (lowest price first - already sorted ASC)
        output.append("§c=== SELL OFFERS (Best Asks) ===\n");
        if (sellDepth.isEmpty()) {
            output.append("§7No sell offers\n");
        } else {
            int count = 0;
            for (java.util.Map.Entry<Integer, Integer> entry : sellDepth.entrySet()) {
                if (count >= 10) break;
                int price = entry.getKey();
                int qty = entry.getValue();
                output.append(String.format("§f%d. §c%d coins §f× §e%d qty §7(Total: %d coins)\n",
                    count + 1, price, qty, price * qty
                ));
                count++;
            }
        }
        
        // Show spread and volume stats
        int spread = depth.getSpread();
        if (spread > 0) {
            int bestBid = depth.getBestBuyPrice();
            int bestAsk = depth.getBestSellPrice();
            double spreadPercent = (spread * 100.0) / bestBid;
            
            output.append(String.format("\n§e=== Market Statistics ===\n"));
            output.append(String.format("§fBest Bid: §a%d §f| Best Ask: §c%d\n", bestBid, bestAsk));
            output.append(String.format("§fSpread: §6%d coins (%.1f%%)\n", spread, spreadPercent));
        }
        
        output.append(String.format("§fTotal Buy Volume: §e%d units\n", depth.getTotalBuyVolume()));
        output.append(String.format("§fTotal Sell Volume: §e%d units\n", depth.getTotalSellVolume()));
        
        // Send to executor
        executor.sendChatMessage(output.toString());
        
        return CommandResult.success(String.format("Market depth for %s displayed", orderBook.getItemStringID()));
    }
}
