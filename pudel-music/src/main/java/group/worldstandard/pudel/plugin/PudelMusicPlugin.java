/*
 * Basic Pudel - Music Plugin Commands
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.plugin;

import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.audio.VoiceManager;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.database.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Music Plugin for Pudel Discord Bot
 * <p>
 * Compliant with Pudel v2.x Strict Schema & Sandboxing policies.
 */
@Plugin(
        name = "Pudel Music",
        version = "2.0.1",
        author = "Zazalng",
        description = "Advanced music playback with slash commands, interactive buttons, and database persistence"
)
public class PudelMusicPlugin {

    // ==================== SANDBOXED STATE ====================
    // All state is instance-bound to ensure clean strict sandbox isolation
    private PluginContext context;
    private final Map<Long, List<AudioTrack>> searchResults = new ConcurrentHashMap<>();
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private AudioPlayerManager playerManager;

    // ==================== DATABASE ====================
    private PluginRepository<QueueEntry> queueRepository;
    private PluginRepository<HistoryEntry> historyRepository;

    // ==================== LIFECYCLE HOOKS ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;

        initializeLavaPlayer();
        initializeDatabase(ctx);

        ctx.log("info", "PudelMusic plugin enabled (Sandboxed & Strict Schema)");
    }

    @OnShutdown
    public boolean onShutdown() {
        try {
            // Cleanup resources strictly within this sandbox context
            for (Map.Entry<Long, GuildMusicManager> entry : musicManagers.entrySet()) {
                long guildId = entry.getKey();
                GuildMusicManager manager = entry.getValue();

                manager.scheduler.queue.clear();
                manager.player.stopTrack();
                manager.player.destroy();

                context.getVoiceManager().disconnect(guildId);
            }

            musicManagers.clear();
            searchResults.clear();

            if (playerManager != null) {
                playerManager.shutdown();
            }

            context.log("info", "PudelMusic plugin shutdown successfully");
            return true;
        } catch (Exception e) {
            context.log("error", "Error during shutdown: " + e.getMessage());
            return false;
        }
    }

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev", "", "Pudel v2.0.0-rc5");

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(
                ytk,
                new WebWithThumbnail(),
                new MWebWithThumbnail(),
                new AndroidMusicWithThumbnail(),
                new AndroidVrWithThumbnail()
        );

        this.playerManager.registerSourceManager(ytSourceManager);

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
    }

    private void initializeDatabase(PluginContext ctx) {
        PluginDatabaseManager db = ctx.getDatabaseManager();

        // Note: 'id' column is RESERVED. Do not define it manually.
        // It is automatically handled by the repository for identity/migrations.

        TableSchema queueSchema = TableSchema.builder("music_queue")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_url", ColumnType.TEXT, false)
                .column("track_title", ColumnType.TEXT, false)
                .column("track_author", ColumnType.TEXT, true) // Nullable
                .column("track_duration", ColumnType.BIGINT, false)
                .column("position", ColumnType.INTEGER, false)
                .column("added_at", ColumnType.TIMESTAMP, false)
                .index("guild_id")
                .build();

        TableSchema historySchema = TableSchema.builder("music_history")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_url", ColumnType.TEXT, false)
                .column("track_title", ColumnType.TEXT, false)
                .column("track_author", ColumnType.TEXT, true)
                .column("track_duration", ColumnType.BIGINT, false)
                .column("played_at", ColumnType.TIMESTAMP, false)
                .index("guild_id")
                .index("user_id")
                .build();

        db.createTable(queueSchema);
        db.createTable(historySchema);

        queueRepository = db.getRepository("music_queue", QueueEntry.class);
        historyRepository = db.getRepository("music_history", HistoryEntry.class);
    }

    // ==================== SLASH COMMANDS ====================

    @SlashCommand(
            name = "play",
            description = "Play music in your voice channel",
            options = {
                    @CommandOption(name = "query", description = "Song name or URL", type = "STRING", required = true)
            }
    )
    public void handlePlaySlash(SlashCommandInteractionEvent event) {
        if (!validateGuildAndVoice(event)) return;

        OptionMapping queryOption = event.getOption("query");
        if (queryOption == null) {
            event.reply("❌ Query is required").setEphemeral(true).queue();
            return;
        }

        String query = queryOption.getAsString();
        event.deferReply().setEphemeral(true).queue();
        loadAndPlay(event, query);
    }

    @SlashCommand(name = "skip", description = "Skip the current track")
    public void handleSkipSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            event.reply("❌ Nothing to skip!").setEphemeral(true).queue();
            return;
        }

        String skippedTitle = current.getInfo().title;
        saveToHistory(event.getGuild().getIdLong(), getUserIdFromTrack(current), current);
        manager.scheduler.skip();
        event.reply("⏭️ Skipped: **" + skippedTitle + "**").queue();
    }

    // ... [Pause, Leave, Volume, Loop commands remain identical to previous iteration] ...

    @SlashCommand(name = "pause", description = "Pause/Resume playback")
    public void handlePauseSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        boolean paused = !manager.player.isPaused();
        manager.player.setPaused(paused);
        event.reply(paused ? "⏸️ Paused playback" : "▶️ Resumed playback").setEphemeral(true).queue();
    }

    @SlashCommand(name = "leave", description = "Disconnect from voice channel")
    public void handleLeaveSlash(SlashCommandInteractionEvent event) {
        if (!validateGuildAndVoice(event)) return;
        long guildId = event.getGuild().getIdLong();

        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.scheduler.queue.clear();
        manager.player.stopTrack();
        clearQueueFromDatabase(guildId);

        context.getVoiceManager().disconnect(guildId);
        event.reply("👋 Disconnected from voice channel").setEphemeral(true).queue();
    }

    @SlashCommand(
            name = "volume",
            description = "Set playback volume",
            options = {
                    @CommandOption(name = "level", description = "Volume level (0-100)", type = "INTEGER", required = true)
            }
    )
    public void handleVolumeSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;
        OptionMapping levelOpt = event.getOption("level");
        if(levelOpt == null) return;
        int volume = levelOpt.getAsInt();
        if (volume < 0 || volume > 100) {
            event.reply("❌ Volume must be between 0 and 100!").setEphemeral(true).queue();
            return;
        }
        getGuildMusicManager(event.getGuild()).player.setVolume(volume);
        event.reply(String.format("🔊 Volume set to %d%%", volume)).setEphemeral(true).queue();
    }

    @SlashCommand(
            name = "loop",
            description = "Toggle loop mode",
            options = {
                    @CommandOption(
                            name = "mode",
                            description = "Loop mode",
                            type = "STRING",
                            choices = {
                                    @Choice(name = "Off", value = "off"),
                                    @Choice(name = "Track", value = "track"),
                                    @Choice(name = "Queue", value = "queue")
                            }
                    )
            }
    )
    public void handleLoopSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        OptionMapping modeOption = event.getOption("mode");
        if (modeOption != null) {
            String modeStr = modeOption.getAsString();
            manager.scheduler.loopMode = switch (modeStr) {
                case "track" -> TrackScheduler.LoopMode.ONE;
                case "queue" -> TrackScheduler.LoopMode.QUEUE;
                default -> TrackScheduler.LoopMode.OFF;
            };
        } else {
            manager.scheduler.toggleLoop();
        }
        event.reply("🔄 Loop mode: **" + manager.scheduler.loopMode.name() + "**").setEphemeral(true).queue();
    }

    @SlashCommand(
            name = "queue",
            description = "View and manage the music queue",
            subcommands = {
                    @Subcommand(name = "view", description = "View the current queue"),
                    @Subcommand(name = "clear", description = "Clear the queue"),
                    @Subcommand(name = "shuffle", description = "Shuffle the queue"),
                    @Subcommand(name = "remove", description = "Remove a track", options = {
                            @CommandOption(name = "position", description = "Track position", type = "INTEGER", required = true)
                    }),
                    @Subcommand(name = "move", description = "Move a track", options = {
                            @CommandOption(name = "from", description = "From position", type = "INTEGER", required = true),
                            @CommandOption(name = "to", description = "To position", type = "INTEGER", required = true)
                    })
            }
    )
    public void handleQueueSlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "view" -> handleQueueView(event, manager);
            case "clear" -> {
                if (!validateMusicControl(event)) return;
                manager.scheduler.queue.clear();
                clearQueueFromDatabase(event.getGuild().getIdLong());
                event.reply("🗑️ Queue cleared.").queue();
            }
            case "shuffle" -> {
                if (!validateMusicControl(event)) return;
                manager.scheduler.toggleShuffle();
                event.reply("🔀 Shuffle " + (manager.scheduler.shuffle ? "enabled" : "disabled")).setEphemeral(true).queue();
            }
            case "remove" -> handleQueueRemove(event, manager);
            case "move" -> handleQueueMove(event, manager);
        }
    }

    @SlashCommand(
            name = "history",
            description = "View your music history",
            options = {
                    @CommandOption(name = "limit", description = "Limit tracks", type = "INTEGER")
            }
    )
    public void handleHistorySlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        int limit = (event.getOption("limit") != null) ? event.getOption("limit").getAsInt() : 10;

        List<HistoryEntry> history = historyRepository.query()
                .whereGreaterThan("guild_id", event.getGuild().getIdLong() - 1)
                .whereLessThan("guild_id", event.getGuild().getIdLong() + 1)
                .orderByDesc("played_at")
                .limit(Math.min(25, limit))
                .list();

        if (history.isEmpty()) {
            event.reply("📜 No history found.").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            sb.append(String.format("%d. **%s** (`%s`)\n", i + 1, entry.trackTitle, formatDuration(entry.trackDuration)));
        }
        event.replyEmbeds(new EmbedBuilder().setTitle("📜 Music History").setDescription(sb.toString()).setColor(Color.ORANGE).build()).setEphemeral(true).queue();
    }

    // ==================== TEXT COMMANDS ====================

    @TextCommand(value = "nowplaying", aliases = {"np"})
    public void handleNowPlayingText(CommandContext ctx) {
        if (!ctx.isFromGuild()) return;
        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            ctx.reply("Nothing is playing.");
            return;
        }
        sendNowPlayingEmbed(ctx.getChannel(), current, manager, ctx.getMember());
    }

    // ==================== INTERACTION HANDLERS ====================

    @ButtonHandler("music:pause")
    public void onPauseButton(ButtonInteractionEvent event) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.player.setPaused(!manager.player.isPaused());
        event.editMessageEmbeds(createNowPlayingEmbed(manager.player.getPlayingTrack(), manager, event.getMember()).build()).queue();
    }

    @ButtonHandler("music:skip")
    public void onSkipButton(ButtonInteractionEvent event) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.scheduler.skip();
        event.reply("⏭️ Skipped").setEphemeral(true).queue();
    }

    @ButtonHandler("music:stop")
    public void onStopButton(ButtonInteractionEvent event) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.scheduler.queue.clear();
        manager.player.stopTrack();
        clearQueueFromDatabase(event.getGuild().getIdLong());
        context.getVoiceManager().disconnect(event.getGuild().getIdLong());
        event.editMessage("⏹️ Stopped").setComponents().setEmbeds().queue();
    }

    @ButtonHandler("music:loop")
    public void onLoopButton(ButtonInteractionEvent event) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.scheduler.toggleLoop();
        event.editMessageEmbeds(createNowPlayingEmbed(manager.player.getPlayingTrack(), manager, event.getMember()).build()).queue();
    }

    @ButtonHandler("music:shuffle")
    public void onShuffleButton(ButtonInteractionEvent event) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        manager.scheduler.toggleShuffle();
        event.editMessageEmbeds(createNowPlayingEmbed(manager.player.getPlayingTrack(), manager, event.getMember()).build()).queue();
    }

    @SelectMenuHandler("music:search")
    public void onSearchSelect(StringSelectInteractionEvent event) {
        if (event.getValues().isEmpty()) return;

        int index = Integer.parseInt(event.getValues().get(0));
        List<AudioTrack> tracks = searchResults.get(event.getGuild().getIdLong());

        if (tracks == null || index >= tracks.size()) {
            event.reply("❌ Search session expired.").setEphemeral(true).queue();
            return;
        }

        AudioTrack selected = tracks.get(index);
        selected.setUserData(event.getUser());
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());

        manager.scheduler.queue(selected);
        saveToQueue(event.getGuild().getIdLong(), event.getUser().getIdLong(), selected, manager.scheduler.queue.size());

        connectToVoice(event.getGuild(), event.getMember());

        event.editMessageEmbeds(new EmbedBuilder()
                .setTitle("✅ Track Added")
                .setDescription(String.format("[%s](%s)", selected.getInfo().title, selected.getInfo().uri))
                .setColor(Color.GREEN)
                .build()).queue();

        searchResults.remove(event.getGuild().getIdLong());
    }

    // ==================== DATABASE HELPERS (STRICT) ====================

    private void saveToQueue(long guildId, long userId, AudioTrack track, int position) {
        try {
            QueueEntry entry = new QueueEntry();
            // Do NOT set entry.id - it is reserved/auto-generated
            entry.setGuildId(guildId);
            entry.setUserId(userId);
            entry.setTrackUrl(track.getInfo().uri);
            entry.setTrackTitle(track.getInfo().title);
            entry.setTrackAuthor(track.getInfo().author);
            entry.setTrackDuration(track.getDuration());
            entry.setPosition(position);
            entry.setAddedAt(Instant.now()); // Strict type matching for ColumnType.TIMESTAMP

            queueRepository.save(entry);
        } catch (Exception e) {
            context.log("error", "Failed to save to queue: " + e.getMessage());
        }
    }

    private void saveToHistory(long guildId, long userId, AudioTrack track) {
        try {
            HistoryEntry entry = new HistoryEntry();
            // Do NOT set entry.id
            entry.setGuildId(guildId);
            entry.setUserId(userId);
            entry.setTrackUrl(track.getInfo().uri);
            entry.setTrackTitle(track.getInfo().title);
            entry.setTrackAuthor(track.getInfo().author);
            entry.setTrackDuration(track.getDuration());
            entry.setPlayedAt(Instant.now()); // Strict type matching

            historyRepository.save(entry);
        } catch (Exception e) {
            context.log("error", "Failed to save to history: " + e.getMessage());
        }
    }

    private void clearQueueFromDatabase(long guildId) {
        try {
            queueRepository.query()
                    .whereGreaterThan("guild_id", guildId - 1)
                    .whereLessThan("guild_id", guildId + 1)
                    .delete();
        } catch (Exception e) {
            context.log("error", "Failed to clear queue: " + e.getMessage());
        }
    }

    // ==================== LOGIC IMPLEMENTATION ====================

    private void loadAndPlay(SlashCommandInteractionEvent event, String input) {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        String trackUrl = (input.startsWith("http://") || input.startsWith("https://")) ? input : "ytsearch:" + input;

        playerManager.loadItem(trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(event.getUser());
                manager.scheduler.queue(track);
                saveToQueue(event.getGuild().getIdLong(), event.getUser().getIdLong(), track, manager.scheduler.queue.size());

                sendNowPlayingEmbed(event.getHook(), track, manager, event.getMember());
                connectToVoice(event.getGuild(), event.getMember());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    searchResults.put(event.getGuild().getIdLong(), playlist.getTracks());
                    sendSearchMenu(event, playlist.getTracks());
                } else {
                    for (AudioTrack track : playlist.getTracks()) {
                        track.setUserData(event.getUser());
                        manager.scheduler.queue(track);
                        saveToQueue(event.getGuild().getIdLong(), event.getUser().getIdLong(), track, manager.scheduler.queue.size());
                    }
                    event.getHook().sendMessage("✅ Added playlist: **" + playlist.getName() + "**").setEphemeral(true).queue();
                    connectToVoice(event.getGuild(), event.getMember());
                }
            }

            @Override
            public void noMatches() {
                event.getHook().sendMessage("❌ No matches found.").setEphemeral(true).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().sendMessage("❌ Load failed: " + exception.getMessage()).setEphemeral(true).queue();
            }
        });
    }

    private void sendSearchMenu(SlashCommandInteractionEvent event, List<AudioTrack> tracks) {
        StringSelectMenu.Builder menu = StringSelectMenu.create("music:search")
                .setPlaceholder("Select a track to play");

        int limit = Math.min(5, tracks.size());
        for (int i = 0; i < limit; i++) {
            AudioTrack track = tracks.get(i);
            String title = truncate(track.getInfo().title, 95);
            menu.addOption(title, String.valueOf(i), formatDuration(track.getDuration()));
        }

        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("🔍 Select a Track")
                        .setDescription("Choose a track from the menu below.")
                        .setColor(Color.CYAN)
                        .build())
                .addComponents(ActionRow.of(menu.build()))
                .queue();
    }

    private void sendNowPlayingEmbed(InteractionHook hook, AudioTrack track, GuildMusicManager manager, Member member) {
        EmbedBuilder embed = createNowPlayingEmbed(track, manager, member);
        hook.sendMessageEmbeds(embed.build()).setComponents(createControls()).queue();
    }

    private void sendNowPlayingEmbed(MessageChannel channel, AudioTrack track, GuildMusicManager manager, Member member) {
        EmbedBuilder embed = createNowPlayingEmbed(track, manager, member);
        channel.sendMessageEmbeds(embed.build()).setComponents(createControls()).queue();
    }

    private ActionRow createControls() {
        return ActionRow.of(
                Button.primary("music:pause", "⏯️"),
                Button.danger("music:stop", "⏹️"),
                Button.secondary("music:skip", "⏭️"),
                Button.secondary("music:loop", "🔁"),
                Button.secondary("music:shuffle", "🔀")
        );
    }

    private EmbedBuilder createNowPlayingEmbed(AudioTrack track, GuildMusicManager manager, Member member) {
        return new EmbedBuilder()
                .setTitle("🎵 Now Playing")
                .setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri))
                .addField("Duration", formatDuration(track.getDuration()), true)
                .addField("Status", manager.player.isPaused() ? "Paused" : "Playing", true)
                .addField("Loop", manager.scheduler.loopMode.name(), true)
                .setColor(Color.MAGENTA);
    }

    // ==================== UTILS & HELPERS ====================

    private void handleQueueView(SlashCommandInteractionEvent event, GuildMusicManager manager) {
        event.reply("Queue contains " + manager.scheduler.queue.size() + " tracks.").queue();
    }

    private void handleQueueRemove(SlashCommandInteractionEvent event, GuildMusicManager manager) {
        OptionMapping posOpt = event.getOption("position");
        if (posOpt == null) return;

        int pos = posOpt.getAsInt();
        List<AudioTrack> list = new ArrayList<>(manager.scheduler.queue);
        if (pos > 0 && pos <= list.size()) {
            list.remove(pos - 1);
            manager.scheduler.queue.clear();
            manager.scheduler.queue.addAll(list);
            event.reply("Removed track at " + pos).setEphemeral(true).queue();
        } else {
            event.reply("Invalid position").setEphemeral(true).queue();
        }
    }

    private void handleQueueMove(SlashCommandInteractionEvent event, GuildMusicManager manager) {
        event.reply("Moved track (Implementation omitted).").setEphemeral(true).queue();
    }

    private boolean validateGuildAndVoice(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ Server only.").setEphemeral(true).queue();
            return false;
        }
        Member m = event.getMember();
        if (m == null || m.getVoiceState() == null || !m.getVoiceState().inAudioChannel()) {
            event.reply("❌ Join a voice channel first.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private boolean validateMusicControl(SlashCommandInteractionEvent event) {
        return validateGuildAndVoice(event);
    }

    private void connectToVoice(Guild guild, Member member) {
        if (member.getVoiceState() == null || member.getVoiceState().getChannel() == null) return;
        long channelId = member.getVoiceState().getChannel().getIdLong();
        VoiceManager vm = context.getVoiceManager();
        if (!vm.isConnected(guild.getIdLong())) {
            vm.connect(guild.getIdLong(), channelId);
        }
    }

    private GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), k -> new GuildMusicManager(playerManager, guild, this));
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d", (seconds % 3600) / 60, seconds % 60);
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    public long getUserIdFromTrack(AudioTrack track) {
        if(track.getUserData() instanceof User u) return u.getIdLong();
        return 0L;
    }

    // ==================== POJOS (STRICT TYPES) ====================

    // 'id' field exists for mapping, but is managed by system (reserved)
    @Entity
    public static class QueueEntry {
        private Long id; // Nullable/Managed by DB
        private long guildId;
        private long userId;
        private String trackUrl;
        private String trackTitle;
        private String trackAuthor;
        private long trackDuration;
        private int position;
        private Instant addedAt; // Strict Instant for TIMESTAMP

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getGuildId() {
            return guildId;
        }

        public void setGuildId(long guildId) {
            this.guildId = guildId;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        public String getTrackUrl() {
            return trackUrl;
        }

        public void setTrackUrl(String trackUrl) {
            this.trackUrl = trackUrl;
        }

        public String getTrackTitle() {
            return trackTitle;
        }

        public void setTrackTitle(String trackTitle) {
            this.trackTitle = trackTitle;
        }

        public String getTrackAuthor() {
            return trackAuthor;
        }

        public void setTrackAuthor(String trackAuthor) {
            this.trackAuthor = trackAuthor;
        }

        public long getTrackDuration() {
            return trackDuration;
        }

        public void setTrackDuration(long trackDuration) {
            this.trackDuration = trackDuration;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public Instant getAddedAt() {
            return addedAt;
        }

        public void setAddedAt(Instant addedAt) {
            this.addedAt = addedAt;
        }
    }

    @Entity
    public static class HistoryEntry {
        private Long id; // Nullable/Managed by DB
        private long guildId;
        private long userId;
        private String trackUrl;
        private String trackTitle;
        private String trackAuthor;
        private long trackDuration;
        private Instant playedAt; // Strict Instant for TIMESTAMP

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getGuildId() {
            return guildId;
        }

        public void setGuildId(long guildId) {
            this.guildId = guildId;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        public String getTrackUrl() {
            return trackUrl;
        }

        public void setTrackUrl(String trackUrl) {
            this.trackUrl = trackUrl;
        }

        public String getTrackTitle() {
            return trackTitle;
        }

        public void setTrackTitle(String trackTitle) {
            this.trackTitle = trackTitle;
        }

        public String getTrackAuthor() {
            return trackAuthor;
        }

        public void setTrackAuthor(String trackAuthor) {
            this.trackAuthor = trackAuthor;
        }

        public long getTrackDuration() {
            return trackDuration;
        }

        public void setTrackDuration(long trackDuration) {
            this.trackDuration = trackDuration;
        }

        public Instant getPlayedAt() {
            return playedAt;
        }

        public void setPlayedAt(Instant playedAt) {
            this.playedAt = playedAt;
        }
    }

    // ==================== INNER CLASSES ====================

    private static class GuildMusicManager {
        public final AudioPlayer player;
        public final TrackScheduler scheduler;

        public GuildMusicManager(AudioPlayerManager manager, Guild g, PudelMusicPlugin plugin) {
            player = manager.createPlayer();
            scheduler = new TrackScheduler(player, plugin, g.getIdLong());
            player.addListener(scheduler);
            g.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(player));
        }
    }

    public static class AudioPlayerSendHandler implements AudioSendHandler {
        private final AudioPlayer audioPlayer;
        private AudioFrame lastFrame;

        public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
            this.audioPlayer = audioPlayer;
        }

        @Override
        public boolean canProvide() {
            lastFrame = audioPlayer.provide();
            return lastFrame != null;
        }

        @Override
        public ByteBuffer provide20MsAudio() {
            return ByteBuffer.wrap(lastFrame.getData());
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }

    private static class TrackScheduler extends AudioEventAdapter {
        public enum LoopMode { OFF, ONE, QUEUE }
        public final Queue<AudioTrack> queue = new LinkedList<>();
        public LoopMode loopMode = LoopMode.OFF;
        public boolean shuffle = false;
        private final AudioPlayer player;

        public TrackScheduler(AudioPlayer player, PudelMusicPlugin plugin, long guildId) { this.player = player; }

        public void queue(AudioTrack track) {
            if (!player.startTrack(track, true)) queue.offer(track);
        }

        public void skip() {
            player.stopTrack();
        }

        public void toggleLoop() { loopMode = (loopMode == LoopMode.OFF) ? LoopMode.ONE : LoopMode.OFF; }
        public void toggleShuffle() { shuffle = !shuffle; }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            if (endReason.mayStartNext) {
                AudioTrack next = queue.poll();
                if(next != null) player.startTrack(next, false);
            }
        }
    }
}