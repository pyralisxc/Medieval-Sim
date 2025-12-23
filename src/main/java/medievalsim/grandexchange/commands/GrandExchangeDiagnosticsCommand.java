package medievalsim.grandexchange.commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import medievalsim.commandcenter.domain.CommandCategory;
import medievalsim.commandcenter.service.AdminCommand;
import medievalsim.commandcenter.service.CommandResult;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.application.GrandExchangeContext;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.GrandExchangeLevelData.DiagnosticsReport;
import medievalsim.grandexchange.domain.GrandExchangeLevelData.DiagnosticsSnapshot;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Admin diagnostics command that emits a concise health report of the
 * Grand Exchange state. Output is intentionally plain text so it can be
 * copied out of the console or chat log without formatting artifacts.
 */
public class GrandExchangeDiagnosticsCommand extends AdminCommand {

    public GrandExchangeDiagnosticsCommand() {
        super(new Builder("gediag", "GE Diagnostics")
            .description("Show Grand Exchange health stats")
            .permission(PermissionLevel.ADMIN)
            .category(CommandCategory.OTHER)
        );
    }

    @Override
    public CommandResult execute(Client client, Server server, ServerClient executor, Object[] args) {
        if (server == null || executor == null) {
            return CommandResult.error("Server context required");
        }
        if (!hasPermission(executor) && !isServerOwner(executor)) {
            executor.sendChatMessage("[GE] Permission denied: admin or world owner only");
            ModLogger.warn("Unauthorized GE diagnostics attempt by auth=%d", executor.authentication);
            return CommandResult.error("Permission denied");
        }

        GrandExchangeContext context = GrandExchangeContext.resolve(server.world.getLevel(executor));
        if (context == null) {
            executor.sendChatMessage("[GE] Grand Exchange data unavailable on this level");
            return CommandResult.error("Grand Exchange not initialized");
        }
        GrandExchangeLevelData geData = context.getLevelData();
        String subcommand = extractSubcommand(args);
        if ("history".equals(subcommand)) {
            int limit = extractHistoryLimit(args);
            List<DiagnosticsSnapshot> history = geData.getDiagnosticsHistory(limit);
            if (history.isEmpty()) {
                executor.sendChatMessage("[GE] No diagnostics snapshots stored yet");
            } else {
                executor.sendChatMessage(String.format("[GE] Showing %d stored snapshots (max %d):", history.size(), limit));
                history.forEach(snapshot -> executor.sendChatMessage(formatHistoryLine(snapshot)));
            }
            return CommandResult.success("Diagnostics history delivered");
        }

        if ("persist".equals(subcommand) || "save".equals(subcommand)) {
            geData.forcePersistenceFlush("gediag");
            executor.sendChatMessage("[GE] Forced persistence snapshot captured");
            return CommandResult.success("Persistence snapshot stored");
        }

        DiagnosticsSnapshot snapshot = geData.captureDiagnosticsSnapshot(describeExecutor(executor), executor.authentication);
        DiagnosticsReport report = snapshot.getReport();

        executor.sendChatMessage(String.format("[GE] === Diagnostics @ %s ===", formatTimestamp(report.getTimestamp())));
        executor.sendChatMessage(String.format("[GE] Requested by: %s", snapshot.getRequestedBy()));
        executor.sendChatMessage(String.format("[GE] Inventories: %d active / %d created", report.getActiveInventories(), report.getTotalInventoriesCreated()));
        executor.sendChatMessage(String.format("[GE] Offers: %d sell active, %d buy active, %d players posting", report.getActiveSellOffers(), report.getActiveBuyOrders(), report.getPlayersWithOffers()));
        executor.sendChatMessage(String.format("[GE] Escrow: %,d coins; Collection backlog: %,d entries across %d players", report.getTotalEscrow(), report.getBacklogEntries(), report.getBacklogPlayers()));
        executor.sendChatMessage(String.format("[GE] Trades: %,d completed; Volume: %,d coins; Tracked items: %d", report.getTotalTrades(), report.getTotalVolume(), report.getTrackedItems()));
        executor.sendChatMessage(String.format("[GE] Rate limit: %d tracked players, %d checks, %.1f%% denied", report.getTrackedRatePlayers(), report.getRateChecks(), report.getDenialRate() * 100f));

        return CommandResult.success("Grand Exchange diagnostics delivered");
    }

    private boolean isServerOwner(ServerClient executor) {
        return executor != null
            && Settings.serverOwnerAuth != -1L
            && executor.authentication == Settings.serverOwnerAuth;
    }

    private String extractSubcommand(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return "";
        }
        return String.valueOf(args[0]).toLowerCase(Locale.ROOT);
    }

    private int extractHistoryLimit(Object[] args) {
        int maxSnapshots = Math.max(5, ModConfig.GrandExchange.diagnosticsHistorySize);
        int defaultLimit = Math.min(5, maxSnapshots);
        if (args == null || args.length < 2 || args[1] == null) {
            return defaultLimit;
        }
        try {
            int requested = Integer.parseInt(String.valueOf(args[1]));
            return Math.max(1, Math.min(maxSnapshots, requested));
        } catch (NumberFormatException ignored) {
            return defaultLimit;
        }
    }

    private String formatHistoryLine(DiagnosticsSnapshot snapshot) {
        DiagnosticsReport report = snapshot.getReport();
        return String.format(
            "[GE] %s | %s | Sell:%d Buy:%d | Backlog %,d/%d | Escrow %,d | Trades %,d (%,d coins) | Rate %.1f%%",
            formatTimestamp(report.getTimestamp()),
            snapshot.getRequestedBy(),
            report.getActiveSellOffers(),
            report.getActiveBuyOrders(),
            report.getBacklogEntries(),
            report.getBacklogPlayers(),
            report.getTotalEscrow(),
            report.getTotalTrades(),
            report.getTotalVolume(),
            report.getDenialRate() * 100f
        );
    }

    private String formatTimestamp(long epochMillis) {
        return HISTORY_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private String describeExecutor(ServerClient executor) {
        if (executor == null) {
            return "system";
        }
        return "auth=" + executor.authentication;
    }

    private static final DateTimeFormatter HISTORY_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
}
