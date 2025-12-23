package medievalsim.packets.registry;

import medievalsim.packets.PacketCreateGuild;
import medievalsim.packets.PacketGuildCreated;
import medievalsim.packets.PacketInviteMember;
import medievalsim.packets.PacketGuildInvited;
import medievalsim.packets.PacketRespondInvite;
import medievalsim.packets.PacketJoinPublicGuild;
import medievalsim.packets.PacketGuildJoined;
import medievalsim.packets.PacketGuildMemberJoined;
import medievalsim.packets.PacketPromoteMember;
import medievalsim.packets.PacketDemoteMember;
import medievalsim.packets.PacketKickMember;
import medievalsim.packets.PacketLeaveGuild;
import medievalsim.packets.PacketDisbandGuild;
import medievalsim.packets.PacketTransferLeadership;
import medievalsim.packets.PacketGuildMemberRankChanged;
import medievalsim.packets.PacketGuildMemberLeft;
import medievalsim.packets.PacketGuildKicked;
import medievalsim.packets.PacketGuildLeft;
import medievalsim.packets.PacketGuildDisbanded;
import medievalsim.packets.PacketGuildLeadershipTransferred;
import medievalsim.packets.PacketRequestGuildInfo;
import medievalsim.packets.PacketGuildInfoResponse;
import medievalsim.packets.PacketListGuilds;
import medievalsim.packets.PacketGuildsListResponse;
import medievalsim.packets.PacketUpdateGuildSymbol;
import medievalsim.packets.PacketGuildSymbolUpdated;
import medievalsim.packets.PacketRequestCrestEditor;
import medievalsim.packets.PacketOpenCrestEditor;
import medievalsim.packets.PacketOpenGuildBank;
import medievalsim.packets.PacketOpenGuildResearch;
import medievalsim.packets.PacketOpenManageGuilds;
import medievalsim.packets.PacketOpenNotifications;
import medievalsim.packets.PacketClearNotification;
import medievalsim.packets.PacketClearAllGuildNotifications;
import medievalsim.packets.PacketRequestBuyBanner;
import medievalsim.packets.PacketBuyGuildBanner;
import medievalsim.packets.PacketRequestGuildBanners;
import medievalsim.packets.PacketGuildBannersResponse;
import medievalsim.packets.PacketRenameGuildBanner;
import medievalsim.packets.PacketUnclaimGuildBanner;
import medievalsim.packets.PacketTeleportToGuildBanner;
import medievalsim.packets.PacketRequestGuildSelection;
import medievalsim.packets.PacketGuildSelectionResponse;
import medievalsim.packets.PacketAcceptGuildUnlockQuest;
import medievalsim.packets.core.PacketSpec;

import java.util.List;

public class GuildPacketRegistrar {
    public static List<PacketSpec> getSpecs() {
        return List.of(
            // Creation & Invitations
            new PacketSpec(PacketCreateGuild.class, "guild", "Client -> server create guild"),
            new PacketSpec(PacketGuildCreated.class, "guild", "Server -> client guild created announcement"),
            new PacketSpec(PacketInviteMember.class, "guild", "Client -> server invite member"),
            new PacketSpec(PacketGuildInvited.class, "guild", "Server -> client invite notification"),
            new PacketSpec(PacketRespondInvite.class, "guild", "Client -> server invite response"),
            new PacketSpec(PacketJoinPublicGuild.class, "guild", "Client -> server join public guild"),
            new PacketSpec(PacketGuildJoined.class, "guild", "Server -> client guild joined confirmation"),
            new PacketSpec(PacketGuildMemberJoined.class, "guild", "Server -> client new member notification"),
            
            // Rank Management
            new PacketSpec(PacketPromoteMember.class, "guild", "Client -> server promote member"),
            new PacketSpec(PacketDemoteMember.class, "guild", "Client -> server demote member"),
            new PacketSpec(PacketGuildMemberRankChanged.class, "guild", "Server -> client rank changed notification"),
            
            // Membership Changes
            new PacketSpec(PacketKickMember.class, "guild", "Client -> server kick member"),
            new PacketSpec(PacketLeaveGuild.class, "guild", "Client -> server leave guild"),
            new PacketSpec(PacketGuildMemberLeft.class, "guild", "Server -> client member left notification"),
            new PacketSpec(PacketGuildKicked.class, "guild", "Server -> client you were kicked notification"),
            new PacketSpec(PacketGuildLeft.class, "guild", "Server -> client you left confirmation"),
            
            // Guild Lifecycle
            new PacketSpec(PacketDisbandGuild.class, "guild", "Client -> server disband guild"),
            new PacketSpec(PacketGuildDisbanded.class, "guild", "Server -> client guild disbanded notification"),
            new PacketSpec(PacketTransferLeadership.class, "guild", "Client -> server transfer leadership"),
            new PacketSpec(PacketGuildLeadershipTransferred.class, "guild", "Server -> client leadership transferred notification"),
            
            // Guild Info Panel
            new PacketSpec(PacketRequestGuildInfo.class, "guild", "Client -> server request full guild info"),
            new PacketSpec(PacketGuildInfoResponse.class, "guild", "Server -> client full guild info response"),
            new PacketSpec(PacketRequestGuildSelection.class, "guild", "Client -> server request guild selection list"),
            new PacketSpec(PacketGuildSelectionResponse.class, "guild", "Server -> client guild selection list response"),
            
            // Guild Browser
            new PacketSpec(PacketListGuilds.class, "guild", "Client -> server request public guilds list"),
            new PacketSpec(PacketGuildsListResponse.class, "guild", "Server -> client guilds list and pending invites"),
            
            // Crest Designer
            new PacketSpec(PacketRequestCrestEditor.class, "guild", "Client -> server request crest editor"),
            new PacketSpec(PacketOpenCrestEditor.class, "guild", "Server -> client open crest editor"),
            new PacketSpec(PacketUpdateGuildSymbol.class, "guild", "Client -> server update guild symbol"),
            new PacketSpec(PacketGuildSymbolUpdated.class, "guild", "Server -> client symbol update confirmation"),
            
            // Guild Bank
            new PacketSpec(PacketOpenGuildBank.class, "guild", "Client -> server request open guild bank"),
            
            // Guild Research
            new PacketSpec(PacketOpenGuildResearch.class, "guild", "Client -> server request open research UI"),
            
            // Manage Guilds (per docs: per-guild leave, buy banner)
            new PacketSpec(PacketOpenManageGuilds.class, "guild", "Client -> server request open manage guilds UI"),
            
            // Notifications (per docs: invites, guild notices, admin messages)
            new PacketSpec(PacketOpenNotifications.class, "guild", "Client -> server request open notifications inbox"),
            new PacketSpec(PacketClearNotification.class, "guild", "Client -> server clear single notification"),
            new PacketSpec(PacketClearAllGuildNotifications.class, "guild", "Client -> server clear all notifications"),
            
            // Banner Purchase (per docs: buy banner with placement/inventory options)
            new PacketSpec(PacketRequestBuyBanner.class, "guild", "Client -> server request open buy banner modal"),
            new PacketSpec(PacketBuyGuildBanner.class, "guild", "Client -> server purchase guild banner"),
            
            // Banner Management (per docs: teleport, rename, unclaim banners)
            new PacketSpec(PacketRequestGuildBanners.class, "guild", "Client -> server request guild banner list"),
            new PacketSpec(PacketGuildBannersResponse.class, "guild", "Server -> client guild banner list response"),
            new PacketSpec(PacketRenameGuildBanner.class, "guild", "Client -> server rename guild banner"),
            new PacketSpec(PacketUnclaimGuildBanner.class, "guild", "Client -> server unclaim guild banner"),
            new PacketSpec(PacketTeleportToGuildBanner.class, "guild", "Client -> server teleport to guild banner"),
            
            // Guild Unlock Quest (per docs: quest offer for players who haven't met requirements)
            new PacketSpec(PacketAcceptGuildUnlockQuest.class, "guild", "Client -> server accept guild unlock quest")
        );
    }
}
