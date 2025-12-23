package medievalsim.grandexchange.application.workflow;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankingService;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

public class BuyOrderWorkflow {
    private final GrandExchangeLedger ledger;

    public BuyOrderWorkflow(GrandExchangeLedger ledger) {
        this.ledger = ledger;
    }

    public Result enable(ServerClient serverClient,
                         PlayerGEInventory inventory,
                         BankingService bankingService,
                         PlayerBank bank,
                         long playerAuth,
                         int slotIndex) {
        if (serverClient == null || inventory == null) {
            return Result.failure();
        }
        if (!isValidBuySlot(slotIndex)) {
            return Result.failure();
        }
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return Result.failure();
        }
        int quantityRemaining = order.getQuantityRemaining();
        if (quantityRemaining <= 0) {
            ModLogger.warn("Buy order slot %d has no remaining quantity", slotIndex);
            return Result.failure();
        }
        
        // Note: The ledger.enableBuyOrder() handles coin withdrawal from bank to escrow.
        // We do NOT call bankingService.withdrawCoins() here as that would double-debit!
        Level level = serverClient.getLevel();
        boolean success = ledger.enableBuyOrder(level, playerAuth, slotIndex);
        if (!success) {
            ModLogger.warn("Ledger failed to enable buy order slot %d for player auth=%d", 
                slotIndex, playerAuth);
            return Result.failure();
        }
        
        int escrowAmount = order.getPricePerItem() * quantityRemaining;
        ModLogger.info("Enabled buy order slot %d for player auth=%d (escrowed %d coins)",
            slotIndex, playerAuth, escrowAmount);
        
        // Return updated bank balance - ledger already deducted coins
        if (bank != null) {
            return Result.coinUpdate(bank.getCoins());
        }
        return Result.success();
    }

    public Result disable(ServerClient serverClient,
                          PlayerGEInventory inventory,
                          BankingService bankingService,
                          PlayerBank bank,
                          long playerAuth,
                          int slotIndex) {
        if (serverClient == null || inventory == null) {
            return Result.failure();
        }
        if (!isValidBuySlot(slotIndex)) {
            return Result.failure();
        }
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return Result.failure();
        }
        
        // Note: The ledger.disableBuyOrder() handles coin refund from escrow to bank.
        // We do NOT call bankingService.depositCoins() here as that would double-credit!
        Level level = serverClient.getLevel();
        boolean success = ledger.disableBuyOrder(level, playerAuth, slotIndex);
        if (!success) {
            ModLogger.warn("Ledger failed to disable buy order slot %d for player auth=%d",
                slotIndex, playerAuth);
            return Result.failure();
        }
        
        int refundAmount = order.getPricePerItem() * order.getQuantityRemaining();
        ModLogger.info("Disabled buy order slot %d, refunded %d coins to bank", slotIndex, refundAmount);
        
        // Return updated bank balance - ledger already refunded coins
        if (bank != null) {
            return Result.coinUpdate(bank.getCoins());
        }
        return Result.success();
    }

    public Result cancel(ServerClient serverClient,
                         PlayerGEInventory inventory,
                         BankingService bankingService,
                         PlayerBank bank,
                         long playerAuth,
                         int slotIndex) {
        if (serverClient == null || inventory == null) {
            return Result.failure();
        }
        if (!isValidBuySlot(slotIndex)) {
            return Result.failure();
        }
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return Result.failure();
        }
        int refundAmount = order.getPricePerItem() * order.getQuantityRemaining();
        
        // Note: The ledger.cancelBuyOrder() handles coin refund from escrow to bank.
        // We do NOT call bankingService.depositCoins() here as that would double-credit!
        Level level = serverClient.getLevel();
        boolean success = ledger.cancelBuyOrder(level, playerAuth, slotIndex);
        if (!success) {
            ModLogger.warn("Ledger failed to cancel buy order slot %d for player auth=%d",
                slotIndex, playerAuth);
            return Result.failure();
        }
        
        long newBalance = bank != null ? bank.getCoins() : 0;
        ModLogger.info("[BANK SYNC] Canceled buy order slot %d, refunded %d coins, new balance=%d",
            slotIndex, refundAmount, newBalance);
        
        // Return updated bank balance - ledger already refunded coins
        if (bank != null) {
            return Result.coinUpdate(newBalance);
        }
        return Result.success();
    }

    private boolean isValidBuySlot(int slot) {
        return slot >= 0 && slot < ModConfig.GrandExchange.buyOrderSlots;
    }

    public static final class Result {
        private final boolean success;
        private final boolean coinBalanceChanged;
        private final long coinBalance;

        private Result(boolean success, boolean coinBalanceChanged, long coinBalance) {
            this.success = success;
            this.coinBalanceChanged = coinBalanceChanged;
            this.coinBalance = coinBalance;
        }

        public static Result success() {
            return new Result(true, false, 0L);
        }

        public static Result failure() {
            return new Result(false, false, 0L);
        }

        public static Result coinUpdate(long coinBalance) {
            return new Result(true, true, coinBalance);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCoinBalanceChanged() {
            return coinBalanceChanged;
        }

        public long getCoinBalance() {
            return coinBalance;
        }
    }
}
