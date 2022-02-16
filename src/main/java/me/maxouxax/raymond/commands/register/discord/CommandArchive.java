package me.maxouxax.raymond.commands.register.discord;

import me.maxouxax.raymond.Raymond;
import me.maxouxax.raymond.commands.Command;
import me.maxouxax.raymond.jda.pojos.ChannelPermission;
import me.maxouxax.raymond.serversconfig.ServerConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class CommandArchive {

    private final Raymond raymond;

    public CommandArchive(){
        this.raymond = Raymond.getInstance();
    }

    @Command(name="archive", power = 150, help = "archive", example = "archive")
    public void archive(User user, TextChannel textChannel, SlashCommandInteractionEvent slashCommandInteractionEvent, String[] args) {
        Guild guild = textChannel.getGuild();
        ServerConfig serverConfig = raymond.getServerConfigsManager().getServerConfig(guild.getId());
        if (serverConfig.isArchived()) {
            slashCommandInteractionEvent.getHook().sendMessage("Ce serveur est déjà archivé, utilisez /unarchive pour le désarchiver").queue();
        } else {
            List<GuildChannel> channelsList = guild.getChannels();
            Role atEveryone = guild.getRolesByName("@everyone", true).get(0);

            HashMap<String, List<ChannelPermission>> permissionBeforeArchive = serverConfig.getPermissionBeforeArchive();
            //making sure there are no permissions in the config, which should be the case if the unarchive process went well
            if(!permissionBeforeArchive.isEmpty())permissionBeforeArchive.clear();

            channelsList.forEach(channel -> {
                permissionBeforeArchive.put(channel.getId(),
                        channel.getPermissionContainer().getPermissionOverrides().stream().map(ChannelPermission::new).collect(Collectors.toList())
                );
                channel.getPermissionContainer().getPermissionOverrides().forEach(permissionOverride -> {
                    permissionOverride.getManager().deny(
                            Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS,
                            Permission.VOICE_CONNECT, Permission.VOICE_STREAM, Permission.VOICE_START_ACTIVITIES)
                            .queue();
                });
            });
            serverConfig.setPermissionBeforeArchive(permissionBeforeArchive, true);
            serverConfig.setArchived(true, true);
            slashCommandInteractionEvent.getHook().sendMessage("Serveur archivé !").queue();
        }
    }

    @Command(name="unarchive", power = 150, help = "unarchive", example = "unarchive")
    public void unarchive(User user, TextChannel textChannel, SlashCommandInteractionEvent slashCommandInteractionEvent, String[] args) {
        Guild guild = textChannel.getGuild();
        ServerConfig serverConfig = raymond.getServerConfigsManager().getServerConfig(guild.getId());
        if (!serverConfig.isArchived()) {
            slashCommandInteractionEvent.getHook().sendMessage("Ce serveur n'est pas archivé, utilisez /archive pour l'archiver").queue();
        } else {
            slashCommandInteractionEvent.getHook().sendMessage("Serveur restauré !").queue();
            serverConfig.setArchived(false, true);
        }
        List<GuildChannel> channelsList = guild.getChannels();
        channelsList.forEach(channel -> {
            HashMap<String, List<ChannelPermission>> permissionBeforeArchive = serverConfig.getPermissionBeforeArchive();
            channel.getPermissionContainer().getPermissionOverrides().forEach(permissionOverride -> permissionOverride.delete().queue());

            permissionBeforeArchive.get(channel.getId()).forEach(channelPermission -> {
                IPermissionHolder permissionHolder = channelPermission.isMemberPermission() ? channel.getGuild().getMemberById(channelPermission.getHolderId()) : channel.getGuild().getRoleById(channelPermission.getHolderId());
                if(permissionHolder != null) {
                    channel.getPermissionContainer().putPermissionOverride(permissionHolder)
                            .setAllow(channelPermission.getAllowedRaw())
                            .setDeny(channelPermission.getDeniedRaw())
                            .queue();
                }
            });
        });
        serverConfig.getPermissionBeforeArchive().clear();
        serverConfig.save();
    }
}
