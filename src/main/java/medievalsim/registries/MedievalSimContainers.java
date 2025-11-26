package medievalsim.registries;

import medievalsim.banking.BankContainer;
import medievalsim.banking.BankContainerForm;
import medievalsim.grandexchange.GrandExchangeContainer;
import medievalsim.grandexchange.GrandExchangeContainerForm;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.registries.ContainerRegistry;

/**
 * Registry for all Medieval Sim custom containers.
 */
public class MedievalSimContainers {
    
    // Container IDs
    public static int BANK_CONTAINER;
    public static int GRAND_EXCHANGE_CONTAINER;
    
    /**
     * Register all containers.
     * Called during mod initialization.
     */
    public static void registerCore() {
        // Bank container
        BANK_CONTAINER = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                BankContainer container = new BankContainer(client.getClient(), uniqueSeed, content);
                return new BankContainerForm<>(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new BankContainer(
                (NetworkClient) client,
                uniqueSeed,
                content
            )
        );

        // Grand Exchange container
        GRAND_EXCHANGE_CONTAINER = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                GrandExchangeContainer container = new GrandExchangeContainer(client.getClient(), uniqueSeed, content);
                return new GrandExchangeContainerForm<>(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new GrandExchangeContainer(
                (NetworkClient) client,
                uniqueSeed,
                content
            )
        );

        ModLogger.info("Registered containers: BANK_CONTAINER=%d, GRAND_EXCHANGE_CONTAINER=%d",
            BANK_CONTAINER, GRAND_EXCHANGE_CONTAINER);
    }
}

