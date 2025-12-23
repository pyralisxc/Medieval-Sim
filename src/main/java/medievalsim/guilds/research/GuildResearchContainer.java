/*
 * Guild Research Container for Medieval Sim Mod
 * Server-side container for research interactions via the Mage NPC.
 */
package medievalsim.guilds.research;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PermissionType;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.ContentCustomAction;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-side container for guild research management.
 * Accessed through the Mage NPC when player is in a guild.
 */
public class GuildResearchContainer extends Container {

    public static int CONTAINER_ID;

    private final int guildID;
    private final GuildRank playerRank;

    // Client-side state (populated via refresh)
    private String activeResearchID = null;
    private long researchProgress = 0;
    private Set<String> completedResearch = new HashSet<>();
    private long researchPoints = 0;

    // Custom actions for research operations
    public final IntCustomAction startResearchAction;
    public final EmptyCustomAction cancelResearchAction;
    public final ContentCustomAction contributeAction;
    public final EmptyCustomAction requestRefreshAction;
    public final ContentCustomAction refreshDataAction;

    public GuildResearchContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);

        PacketReader reader = new PacketReader(content);
        this.guildID = reader.getNextInt();
        this.playerRank = GuildRank.fromLevel(reader.getNextInt());
        
        // Read initial state from opening packet
        boolean hasActive = reader.getNextBoolean();
        if (hasActive) {
            this.activeResearchID = reader.getNextString();
            this.researchProgress = reader.getNextLong();
        }
        int completedCount = reader.getNextInt();
        for (int i = 0; i < completedCount; i++) {
            this.completedResearch.add(reader.getNextString());
        }
        this.researchPoints = reader.getNextLong();

        // === Custom Actions ===

        // Start research project (by registry index)
        this.startResearchAction = registerAction(new IntCustomAction() {
            @Override
            protected void run(int projectIndex) {
                if (!client.isServer()) return;

                ServerClient serverClient = client.getServerClient();
                if (serverClient == null) return;

                GuildManager manager = GuildManager.get(serverClient.getServer().world);
                if (manager == null) return;

                GuildData guild = manager.getGuild(guildID);
                if (guild == null) return;

                // Check permission
                if (!guild.hasPermission(serverClient.authentication, PermissionType.MODIFY_RESEARCH)) {
                    ModLogger.warn("Player lacks permission to start research");
                    return;
                }

                // Get project by index
                Collection<ResearchProject> allProjects = ResearchRegistry.getAllProjects();
                int i = 0;
                ResearchProject targetProject = null;
                for (ResearchProject p : allProjects) {
                    if (i == projectIndex) {
                        targetProject = p;
                        break;
                    }
                    i++;
                }

                if (targetProject != null) {
                    GuildResearchManager research = guild.getResearchManager();
                    boolean success = research.startResearch(targetProject.getId(), guild, serverClient.authentication);
                    if (success) {
                        ModLogger.info("Guild %d started research: %s", guildID, targetProject.getId());
                        // Send refresh to client
                        sendRefresh(guild);
                    }
                }
            }
        });

        // Cancel active research
        this.cancelResearchAction = registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (!client.isServer()) return;

                ServerClient serverClient = client.getServerClient();
                if (serverClient == null) return;

                GuildManager manager = GuildManager.get(serverClient.getServer().world);
                if (manager == null) return;

                GuildData guild = manager.getGuild(guildID);
                if (guild == null) return;

                // Check permission
                if (!guild.hasPermission(serverClient.authentication, PermissionType.MODIFY_RESEARCH)) {
                    return;
                }

                guild.getResearchManager().cancelResearch(guild, serverClient.authentication);
                sendRefresh(guild);
            }
        });

        // Contribute resources to research
        this.contributeAction = registerAction(new ContentCustomAction() {
            @Override
            protected void run(Packet contributionData) {
                if (!client.isServer()) return;

                PacketReader contribReader = new PacketReader(contributionData);
                long points = contribReader.getNextLong();

                ServerClient serverClient = client.getServerClient();
                if (serverClient == null) return;

                GuildManager manager = GuildManager.get(serverClient.getServer().world);
                if (manager == null) return;

                GuildData guild = manager.getGuild(guildID);
                if (guild == null) return;

                // Add research points
                guild.getResearchManager().addResearchPoints(points);

                // Check if research completed
                if (guild.getResearchManager().checkCompletion(guild)) {
                    ModLogger.info("Guild %d completed research!", guildID);
                }

                sendRefresh(guild);
            }
        });

        // Request refresh from server (client-side action)
        this.requestRefreshAction = registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (!client.isServer()) return;
                
                ServerClient serverClient = client.getServerClient();
                if (serverClient == null) return;

                GuildManager manager = GuildManager.get(serverClient.getServer().world);
                if (manager == null) return;

                GuildData guild = manager.getGuild(guildID);
                if (guild != null) {
                    sendRefresh(guild);
                }
            }
        });
        
        // Receive refresh data from server (server→client)
        this.refreshDataAction = registerAction(new ContentCustomAction() {
            @Override
            protected void run(Packet refreshData) {
                if (client.isServer()) return; // Only handle on client
                
                PacketReader refreshReader = new PacketReader(refreshData);
                
                // Read active research
                boolean hasActive = refreshReader.getNextBoolean();
                if (hasActive) {
                    activeResearchID = refreshReader.getNextString();
                    researchProgress = refreshReader.getNextLong();
                } else {
                    activeResearchID = null;
                    researchProgress = 0;
                }
                
                // Read completed research
                completedResearch.clear();
                int completedCount = refreshReader.getNextInt();
                for (int i = 0; i < completedCount; i++) {
                    completedResearch.add(refreshReader.getNextString());
                }
                
                // Read research points
                researchPoints = refreshReader.getNextLong();
            }
        });
    }

    // === Getters for form access ===
    
    public String getActiveResearchID() {
        return activeResearchID;
    }
    
    public long getResearchProgress() {
        return researchProgress;
    }
    
    public Set<String> getCompletedResearch() {
        return completedResearch;
    }
    
    public long getResearchPoints() {
        return researchPoints;
    }

    public int getGuildID() {
        return guildID;
    }

    public GuildRank getPlayerRank() {
        return playerRank;
    }

    public boolean canModifyResearch() {
        return playerRank.getLevel() >= GuildRank.OFFICER.getLevel();
    }

    private void sendRefresh(GuildData guild) {
        Packet refreshPacket = new Packet();
        PacketWriter writer = new PacketWriter(refreshPacket);

        GuildResearchManager research = guild.getResearchManager();

        // Write active research
        String activeId = research.getActiveResearchID();
        writer.putNextBoolean(activeId != null);
        if (activeId != null) {
            writer.putNextString(activeId);
            writer.putNextLong(research.getResearchProgress());
        }

        // Write completed research
        Set<String> completed = research.getCompletedResearch();
        writer.putNextInt(completed.size());
        for (String id : completed) {
            writer.putNextString(id);
        }

        // Write research points
        writer.putNextLong(research.getResearchPoints());

        refreshDataAction.runAndSend(refreshPacket);
    }

    // === Registration ===

    public static void registerContainer() {
        CONTAINER_ID = ContainerRegistry.registerContainer(
            // Client handler - creates the UI form
            (client, uniqueSeed, content) -> {
                GuildResearchContainer container = new GuildResearchContainer(
                    client.getClient(), uniqueSeed, content);
                return new GuildResearchContainerForm(client, container);
            },
            // Server handler - creates the server-side container
            (client, uniqueSeed, content, serverObject) -> new GuildResearchContainer(
                (NetworkClient) client, uniqueSeed, content)
        );

        ModLogger.info("Registered GuildResearchContainer: ID=%d", CONTAINER_ID);
    }

    /**
     * Open the research UI for a player.
     */
    public static void openResearchUI(ServerClient serverClient, int guildID, GuildRank playerRank, GuildData guild) {
        Packet content = new Packet();
        PacketWriter writer = new PacketWriter(content);
        writer.putNextInt(guildID);
        writer.putNextInt(playerRank.getLevel());
        
        // Include initial research state
        GuildResearchManager research = guild.getResearchManager();
        
        String activeId = research.getActiveResearchID();
        writer.putNextBoolean(activeId != null);
        if (activeId != null) {
            writer.putNextString(activeId);
            writer.putNextLong(research.getResearchProgress());
        }
        
        Set<String> completed = research.getCompletedResearch();
        writer.putNextInt(completed.size());
        for (String id : completed) {
            writer.putNextString(id);
        }
        
        writer.putNextLong(research.getResearchPoints());

        PacketOpenContainer openPacket = new PacketOpenContainer(CONTAINER_ID, content);
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);

        ModLogger.debug("Opened research UI for player, guildID=%d", guildID);
    }
}
