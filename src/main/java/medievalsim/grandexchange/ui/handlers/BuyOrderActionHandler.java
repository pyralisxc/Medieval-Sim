package medievalsim.grandexchange.ui.handlers;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankingService;
import medievalsim.grandexchange.application.workflow.BuyOrderWorkflow;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;

/**
 * Handles all buy order actions for the Grand Exchange.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Operations handled:</p>
 * <ul>
 *   <li>Enable - Escrow coins from bank to activate the order</li>
 *   <li>Disable - Refund coins to bank, deactivate order</li>
 *   <li>Cancel - Remove order entirely, refund any escrowed coins</li>
 * </ul>
 * 
 * <p>Note: Buy order creation is handled via PacketGECreateBuyOrder,
 * not through this handler (creation requires form input).</p>
 */
public class BuyOrderActionHandler {

    private final ServerClient serverClient;
    private final long playerAuth;
    private final PlayerGEInventory playerInventory;
    private final BuyOrderWorkflow buyWorkflow;
    private final BankingService bankingService;
    private final PlayerBank bank;
    private final ContainerSyncManager syncManager;

    public BuyOrderActionHandler(
            ServerClient serverClient,
            long playerAuth,
            PlayerGEInventory playerInventory,
            BuyOrderWorkflow buyWorkflow,
            BankingService bankingService,
            PlayerBank bank,
            ContainerSyncManager syncManager) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.playerInventory = playerInventory;
        this.buyWorkflow = buyWorkflow;
        this.bankingService = bankingService;
        this.bank = bank;
        this.syncManager = syncManager;
    }

    // ===== ENABLE BUY ORDER =====

    /**
     * Enable a buy order by escrowing coins from the player's bank.
     */
    public void handleEnable(int slotIndex) {
        if (serverClient == null || buyWorkflow == null) {
            syncManager.sendBuyOrderSync(); // Always sync to clear pending state
            return;
        }
        
        BuyOrderWorkflow.Result result = buyWorkflow.enable(
            serverClient,
            playerInventory,
            bankingService,
            bank,
            playerAuth,
            slotIndex
        );
        
        if (!result.isSuccess()) {
            syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.ERROR,
                "Failed to enable buy order. Check your bank balance.");
            syncManager.sendBuyOrderSync(); // Sync even on failure to clear pending state
            return;
        }
        
        if (result.isCoinBalanceChanged()) {
            syncManager.syncCoinCount(result.getCoinBalance());
        }
        
        syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.INFO,
            "Buy order enabled. Coins escrowed from bank.");
        syncManager.sendBuyOrderSync();
    }

    // ===== DISABLE BUY ORDER =====

    /**
     * Disable a buy order and refund escrowed coins to the player's bank.
     */
    public void handleDisable(int slotIndex) {
        if (serverClient == null || buyWorkflow == null) {
            syncManager.sendBuyOrderSync(); // Always sync to clear pending state
            return;
        }
        
        BuyOrderWorkflow.Result result = buyWorkflow.disable(
            serverClient,
            playerInventory,
            bankingService,
            bank,
            playerAuth,
            slotIndex
        );
        
        if (!result.isSuccess()) {
            syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.ERROR,
                "Failed to disable buy order.");
            syncManager.sendBuyOrderSync(); // Sync even on failure to clear pending state
            return;
        }
        
        if (result.isCoinBalanceChanged()) {
            syncManager.syncCoinCount(result.getCoinBalance());
        }
        
        syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.INFO,
            "Buy order disabled. Coins refunded to bank.");
        syncManager.sendBuyOrderSync();
    }

    // ===== CANCEL BUY ORDER =====

    /**
     * Cancel a buy order entirely and refund any escrowed coins.
     */
    public void handleCancel(int slotIndex) {
        if (serverClient == null || buyWorkflow == null) {
            syncManager.sendBuyOrderSync(); // Always sync to clear pending state
            return;
        }
        
        BuyOrderWorkflow.Result result = buyWorkflow.cancel(
            serverClient,
            playerInventory,
            bankingService,
            bank,
            playerAuth,
            slotIndex
        );
        
        if (!result.isSuccess()) {
            syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.ERROR,
                "Failed to cancel buy order.");
            syncManager.sendBuyOrderSync(); // Sync even on failure to clear pending state
            return;
        }
        
        if (result.isCoinBalanceChanged()) {
            syncManager.syncCoinCount(result.getCoinBalance());
        }
        
        syncManager.sendFeedback(GEFeedbackChannel.BUY, GEFeedbackLevel.INFO,
            "Buy order cancelled. Coins refunded to bank.");
        syncManager.sendBuyOrderSync();
    }
}
