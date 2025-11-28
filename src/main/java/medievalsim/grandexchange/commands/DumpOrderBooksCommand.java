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

import java.util.Map;

/**
 * Admin command: dump order books summary for debugging.
 * Usage: /gedumpbooks
 */
public class DumpOrderBooksCommand extends AdminCommand {
    public DumpOrderBooksCommand() {
        super(new Builder("gedumpbooks", "GE Dump OrderBooks")
            .description("Debug: dump all OrderBooks and counts")
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
        if (server == null || executor == null) {
            return CommandResult.error("Server context required");
        }

        GrandExchangeLevelData geData = GrandExchangeLevelData.getGrandExchangeData(server.world.getLevel(executor));
        if (geData == null) {
            return CommandResult.error("Grand Exchange not available in this world");
        }

        Map<String, OrderBook> orderBooks = geData.getOrderBooksByItem();
        if (orderBooks.isEmpty()) {
            executor.sendChatMessage("[GE] No active order books");
            return CommandResult.success("No active order books");
        }

        executor.sendChatMessage(String.format("[GE] Dumping %d order books:", orderBooks.size()));
        int index = 0;
        for (Map.Entry<String, OrderBook> entry : orderBooks.entrySet()) {
            OrderBook book = entry.getValue();
            String line = String.format("%d) %s — buys=%d sells=%d matches=%d",
                ++index,
                book.getItemStringID(),
                book.getBuyOrderCount(),
                book.getSellOfferCount(),
                book.getTotalMatches()
            );
            executor.sendChatMessage(line);
        }

        return CommandResult.success(String.format("Dumped %d order books to chat", orderBooks.size()));
    }
}
