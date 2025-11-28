package medievalsim.grandexchange.repository;

import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.util.ModLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of OfferRepository.
 * 
 * Uses ConcurrentHashMap for thread-safe storage.
 * Maintains indexes for fast lookups by item and player.
 * 
 * Future: Can be replaced with database implementation without changing business logic.
 */
public class InMemoryOfferRepository implements OfferRepository {
    
    // Primary storage
    private final Map<Long, GEOffer> sellOffers = new ConcurrentHashMap<>();
    private final Map<Long, BuyOrder> buyOrders = new ConcurrentHashMap<>();
    
    // Indexes for fast lookups
    private final Map<String, List<Long>> sellOffersByItem = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> sellOffersByPlayer = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> buyOrdersByItem = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> buyOrdersByPlayer = new ConcurrentHashMap<>();
    
    // ===== SELL OFFER OPERATIONS =====
    
    @Override
    public GEOffer saveSellOffer(GEOffer offer) {
        Objects.requireNonNull(offer, "offer cannot be null");
        long offerID = offer.getOfferID();

        // Put and get previous to handle re-indexing if offer was updated
        GEOffer previous = sellOffers.put(offerID, offer);

        // If previous existed and item/player changed, remove from old indexes
            if (previous != null) {
            String prevItem = previous.getItemStringID();
            String newItem = offer.getItemStringID();
            if (!Objects.equals(prevItem, newItem) && prevItem != null) {
                    // Atomically remove the offerID from the previous item list; if the list becomes
                    // empty return null to remove the map entry.
                    sellOffersByItem.computeIfPresent(prevItem, (k, list) -> {
                        synchronized (list) {
                            list.remove(offerID);
                            return list.isEmpty() ? null : list;
                        }
                    });
            }

            Long prevPlayer = previous.getPlayerAuth();
            Long newPlayer = offer.getPlayerAuth();
            if (!Objects.equals(prevPlayer, newPlayer) && prevPlayer != null) {
                    sellOffersByPlayer.computeIfPresent(prevPlayer, (k, list) -> {
                        synchronized (list) {
                            list.remove(offerID);
                            return list.isEmpty() ? null : list;
                        }
                    });
            }
        }

        // Update indexes (add to new lists if missing)
        if (offer.getItemStringID() != null) {
            List<Long> itemOffers = sellOffersByItem.computeIfAbsent(offer.getItemStringID(), k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (itemOffers) {
                if (!itemOffers.contains(offerID)) {
                    itemOffers.add(offerID);
                }
            }
        }

        List<Long> playerOffers = sellOffersByPlayer.computeIfAbsent(offer.getPlayerAuth(), k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (playerOffers) {
            if (!playerOffers.contains(offerID)) {
                playerOffers.add(offerID);
            }
        }

        ModLogger.debug("Saved sell offer ID=%d to repository", offerID);
        return offer;
    }
    
    @Override
    public Optional<GEOffer> findSellOfferById(long offerID) {
        return Optional.ofNullable(sellOffers.get(offerID));
    }
    
    @Override
    public List<GEOffer> findActiveSellOffersByItem(String itemID) {
        List<Long> offerIDs = sellOffersByItem.get(itemID);
        if (offerIDs == null) {
            return Collections.emptyList();
        }

        List<Long> snapshot;
        synchronized (offerIDs) {
            snapshot = new ArrayList<>(offerIDs);
        }

        return snapshot.stream()
            .map(sellOffers::get)
            .filter(o -> o != null && o.isActive())
            .sorted(Comparator.comparingInt(GEOffer::getPricePerItem))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<GEOffer> findSellOffersByPlayer(long playerAuth) {
        List<Long> offerIDs = sellOffersByPlayer.get(playerAuth);
        if (offerIDs == null) {
            return Collections.emptyList();
        }

        List<Long> snapshot;
        synchronized (offerIDs) {
            snapshot = new ArrayList<>(offerIDs);
        }

        return snapshot.stream()
            .map(sellOffers::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<GEOffer> findAllActiveSellOffers() {
        return sellOffers.values().stream()
            .filter(GEOffer::isActive)
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean deleteSellOffer(long offerID) {
        GEOffer offer = sellOffers.remove(offerID);
        if (offer == null) {
            return false;
        }
        
        // Remove from indexes and clean up empty lists
        if (offer.getItemStringID() != null) {
            sellOffersByItem.computeIfPresent(offer.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(offerID);
                    return list.isEmpty() ? null : list;
                }
            });
        }

        sellOffersByPlayer.computeIfPresent(offer.getPlayerAuth(), (k, list) -> {
            synchronized (list) {
                list.remove(offerID);
                return list.isEmpty() ? null : list;
            }
        });
        
        ModLogger.debug("Deleted sell offer ID=%d from repository", offerID);
        return true;
    }
    
    @Override
    public int countActiveSellOffersForItem(String itemID) {
        return (int) findActiveSellOffersByItem(itemID).size();
    }
    
    // ===== BUY ORDER OPERATIONS =====
    
    @Override
    public BuyOrder saveBuyOrder(BuyOrder order) {
        Objects.requireNonNull(order, "order cannot be null");
        long orderID = order.getOrderID();

        // Put and get previous to handle re-indexing if order was updated
        BuyOrder previous = buyOrders.put(orderID, order);

        if (previous != null) {
            String prevItem = previous.getItemStringID();
            String newItem = order.getItemStringID();
            if (!Objects.equals(prevItem, newItem) && prevItem != null) {
                buyOrdersByItem.computeIfPresent(prevItem, (k, list) -> {
                    synchronized (list) {
                        list.remove(orderID);
                        return list.isEmpty() ? null : list;
                    }
                });
            }

            Long prevPlayer = previous.getPlayerAuth();
            Long newPlayer = order.getPlayerAuth();
            if (!Objects.equals(prevPlayer, newPlayer) && prevPlayer != null) {
                buyOrdersByPlayer.computeIfPresent(prevPlayer, (k, list) -> {
                    synchronized (list) {
                        list.remove(orderID);
                        return list.isEmpty() ? null : list;
                    }
                });
            }
        }

        // Update indexes (prevent duplicates)
        if (order.getItemStringID() != null) {
            List<Long> itemOrders = buyOrdersByItem.computeIfAbsent(order.getItemStringID(), k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (itemOrders) {
                if (!itemOrders.contains(orderID)) {
                    itemOrders.add(orderID);
                }
            }
        }

        List<Long> playerOrders = buyOrdersByPlayer.computeIfAbsent(order.getPlayerAuth(), k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (playerOrders) {
            if (!playerOrders.contains(orderID)) {
                playerOrders.add(orderID);
            }
        }

        ModLogger.debug("Saved buy order ID=%d to repository", orderID);
        return order;
    }
    
    @Override
    public Optional<BuyOrder> findBuyOrderById(long orderID) {
        return Optional.ofNullable(buyOrders.get(orderID));
    }
    
    @Override
    public List<BuyOrder> findActiveBuyOrdersByItem(String itemID) {
        List<Long> orderIDs = buyOrdersByItem.get(itemID);
        if (orderIDs == null) {
            return Collections.emptyList();
        }

        List<Long> snapshot;
        synchronized (orderIDs) {
            snapshot = new ArrayList<>(orderIDs);
        }

        return snapshot.stream()
            .map(buyOrders::get)
            .filter(o -> o != null && o.canMatch())
            .sorted(Comparator.comparingInt(BuyOrder::getPricePerItem).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BuyOrder> findBuyOrdersByPlayer(long playerAuth) {
        List<Long> orderIDs = buyOrdersByPlayer.get(playerAuth);
        if (orderIDs == null) {
            return Collections.emptyList();
        }

        List<Long> snapshot;
        synchronized (orderIDs) {
            snapshot = new ArrayList<>(orderIDs);
        }

        return snapshot.stream()
            .map(buyOrders::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BuyOrder> findAllActiveBuyOrders() {
        return buyOrders.values().stream()
            .filter(BuyOrder::canMatch)
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean deleteBuyOrder(long orderID) {
        BuyOrder order = buyOrders.remove(orderID);
        if (order == null) {
            return false;
        }
        
        // Remove from indexes and clean up empty lists
        if (order.getItemStringID() != null) {
            buyOrdersByItem.computeIfPresent(order.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(orderID);
                    return list.isEmpty() ? null : list;
                }
            });
        }

        buyOrdersByPlayer.computeIfPresent(order.getPlayerAuth(), (k, list) -> {
            synchronized (list) {
                list.remove(orderID);
                return list.isEmpty() ? null : list;
            }
        });
        
        ModLogger.debug("Deleted buy order ID=%d from repository", orderID);
        return true;
    }
    
    @Override
    public int countActiveBuyOrdersForItem(String itemID) {
        return (int) findActiveBuyOrdersByItem(itemID).size();
    }
    
    // ===== UTILITY OPERATIONS =====
    
    @Override
    public int countTotalActiveOffers() {
        return (int) (sellOffers.values().stream().filter(GEOffer::isActive).count() +
                     buyOrders.values().stream().filter(BuyOrder::canMatch).count());
    }
    
    @Override
    public List<String> findAllActiveItems() {
        Set<String> items = new HashSet<>();
        items.addAll(sellOffersByItem.keySet());
        items.addAll(buyOrdersByItem.keySet());
        return new ArrayList<>(items);
    }
    
    @Override
    public void clearAll() {
        sellOffers.clear();
        buyOrders.clear();
        sellOffersByItem.clear();
        sellOffersByPlayer.clear();
        buyOrdersByItem.clear();
        buyOrdersByPlayer.clear();
        ModLogger.info("Cleared all offers and orders from repository");
    }
    
    /**
     * Get total storage counts (for monitoring).
     */
    public Map<String, Integer> getStorageStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("sellOffers", sellOffers.size());
        stats.put("buyOrders", buyOrders.size());
        stats.put("sellOffersByItem", sellOffersByItem.size());
        stats.put("buyOrdersByItem", buyOrdersByItem.size());
        return stats;
    }
}
