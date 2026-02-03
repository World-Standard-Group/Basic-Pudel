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

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import group.worldstandard.pudel.api.SimplePlugin;
import group.worldstandard.pudel.api.audio.VoiceConnectionStatus;
import group.worldstandard.pudel.api.audio.VoiceManager;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.TableSchema;
import group.worldstandard.pudel.api.event.EventHandler;
import group.worldstandard.pudel.api.event.Listener;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced Music Plugin for Pudel Discord Bot
 * <p>
 * Features:
 * - Slash command interactions with component-based UI
 * - LavaPlayer integration with YouTube support
 * - PostgreSQL database for queue/history persistence
 * - Interactive reaction controls
 * - DAVE voice encryption support
 * - Multi-guild support
 *
 * @author Zazalng
 * @version 2.0.0
 */
public class PudelMusicPlugin extends SimplePlugin implements Listener {

    // ==================== CONSTANTS ====================
    private static final String PLUGIN_ID = "pudel-music";

    // ==================== STATE MANAGEMENT ====================
    private final Map<Long, List<AudioTrack>> searchResults = new ConcurrentHashMap<>();
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, Long> playerMessages = new ConcurrentHashMap<>();
    private final Map<Long, Long> searchMessages = new ConcurrentHashMap<>();
    private AudioPlayerManager playerManager;

    // ==================== DATABASE ====================
    private PluginRepository<QueueEntry> queueRepository;
    private PluginRepository<HistoryEntry> historyRepository;

    // ==================== PLUGIN INITIALIZATION ====================
    public PudelMusicPlugin() {
        super(
                "Pudel Music",
                "2.0.0",
                "Zazalng",
                "Advanced music playback with slash commands, interactive controls, and database persistence"
        );
    }

    @Override
    protected void setup() {
        initializeLavaPlayer();
        initializeDatabase();
        registerSlashCommands();

        // Register reaction listener
        listener(this);

        // Keep text commands only for np/nowplaying
        command("nowplaying", this::handleNowPlayingText);
        command("np", this::handleNowPlayingText);

        log("info", "PudelMusic plugin initialized with slash commands and database support");
    }

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev/", "", "Pudel v2.0.0");

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(ytk, new AndroidVr());

        this.playerManager.registerSourceManager(ytSourceManager);

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );

        log("info", "LavaPlayer initialized with YouTube support");
    }

    private void initializeDatabase() {
        PluginDatabaseManager db = getContext().getDatabaseManager();

        // Queue table schema
        TableSchema queueSchema = TableSchema.builder("music_queue")
                .column("id", ColumnType.BIGINT, false)
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_url", ColumnType.TEXT, false)
                .column("track_title", ColumnType.TEXT, false)
                .column("track_author", ColumnType.TEXT, true)
                .column("track_duration", ColumnType.BIGINT, false)
                .column("position", ColumnType.INTEGER, false)
                .column("added_at", ColumnType.TIMESTAMP, false)
                .index("guild_id")
                .build();

        // History table schema
        TableSchema historySchema = TableSchema.builder("music_history")
                .column("id", ColumnType.BIGINT, false)
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

        log("info", "Database tables initialized for queue and history");
    }

    // ==================== SLASH COMMANDS REGISTRATION ====================

    private void registerSlashCommands() {
        InteractionManager manager = getContext().getInteractionManager();

        // /play command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("play", "Play music in your voice channel")
                        .addOption(OptionType.STRING, "query", "Song name or URL to play", true);
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handlePlaySlash(event);
            }
        });

        // /skip command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("skip", "Skip the current track");
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleSkipSlash(event);
            }
        });

        // /queue command with subcommands
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("queue", "View and manage the music queue")
                        .addSubcommands(
                                new SubcommandData("view", "View the current queue"),
                                new SubcommandData("clear", "Clear the queue"),
                                new SubcommandData("shuffle", "Shuffle the queue"),
                                new SubcommandData("remove", "Remove a track from the queue")
                                        .addOption(OptionType.INTEGER, "position", "Position of the track to remove (1-based)", true),
                                new SubcommandData("move", "Move a track to a new position")
                                        .addOption(OptionType.INTEGER, "from", "Current position of the track (1-based)", true)
                                        .addOption(OptionType.INTEGER, "to", "New position for the track (1-based)", true)
                        );
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleQueueSlash(event);
            }
        });

        // /history command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("history", "View your music history")
                        .addOption(OptionType.INTEGER, "limit", "Number of tracks to show (default: 10)", false);
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleHistorySlash(event);
            }
        });

        // /volume command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("volume", "Set the playback volume")
                        .addOption(OptionType.INTEGER, "level", "Volume level (0-100)", true);
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleVolumeSlash(event);
            }
        });

        // /leave command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("leave", "Disconnect from voice channel");
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleLeaveSlash(event);
            }
        });

        // /loop command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("loop", "Toggle loop mode")
                        .addOption(OptionType.STRING, "mode", "Loop mode: off, track, queue", false);
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handleLoopSlash(event);
            }
        });

        // /pause command
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("pause", "Pause/Resume playback");
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handlePauseSlash(event);
            }
        });

        // Sync commands to Discord
        manager.syncCommands();

        log("info", "Slash commands registered");
    }

    // ==================== SLASH COMMAND HANDLERS ====================

    private void handlePlaySlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to play music!").setEphemeral(true).queue();
            return;
        }

        OptionMapping queryOption = event.getOption("query");
        if (queryOption == null) {
            event.reply("❌ Please provide a song name or URL!").setEphemeral(true).queue();
            return;
        }

        String query = queryOption.getAsString();
        event.deferReply().queue();

        loadAndPlay(event, query);
    }

    private void loadAndPlay(SlashCommandInteractionEvent event, String input) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        long guildId = guild.getIdLong();
        GuildMusicManager manager = getGuildMusicManager(guild);

        boolean isUrl = input.startsWith("http://") || input.startsWith("https://");
        String trackUrl = isUrl ? input : "ytsearch:" + input;

        playerManager.loadItem(trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(event.getUser());
                manager.scheduler.queue(track);

                // Save to database queue
                saveToQueue(guildId, event.getUser().getIdLong(), track, manager.scheduler.queue.size());

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("✅ Added to Queue")
                        .setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri))
                        .setThumbnail(getTrackThumbnail(track))
                        .addField("Uploader", track.getInfo().author, true)
                        .addField("Duration", formatDuration(track.getDuration()), true)
                        .addField("Position", String.valueOf(manager.scheduler.queue.size()), true)
                        .setColor(Color.GREEN)
                        .setFooter("Use reactions to control • ⏯️ ⏹️ ⏭️ 🔁 🔀")
                        .setTimestamp(Instant.now());

                event.getHook().sendMessageEmbeds(embed.build()).queue(msg -> {
                    playerMessages.put(guildId, msg.getIdLong());
                    addPlayerReactions(msg);
                });

                connectToVoice(event);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    List<AudioTrack> tracks = playlist.getTracks().subList(0, Math.min(5, playlist.getTracks().size()));
                    searchResults.put(guildId, tracks);
                    showSearchResults(event, tracks, input);
                } else {
                    int addedCount = 0;
                    for (AudioTrack track : playlist.getTracks()) {
                        track.setUserData(event.getUser());
                        manager.scheduler.queue(track);
                        saveToQueue(guildId, event.getUser().getIdLong(), track, manager.scheduler.queue.size());
                        addedCount++;
                    }

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("📋 Playlist Added")
                            .setDescription(String.format("**%s**", playlist.getName()))
                            .setThumbnail("https://puu.sh/KgxX3.gif")
                            .addField("Tracks", String.valueOf(addedCount), true)
                            .setColor(Color.BLUE)
                            .setTimestamp(Instant.now());

                    event.getHook().sendMessageEmbeds(embed.build()).queue(msg -> {
                        playerMessages.put(guildId, msg.getIdLong());
                        addPlayerReactions(msg);
                    });

                    connectToVoice(event);
                }
            }

            @Override
            public void noMatches() {
                event.getHook().sendMessage("❌ No results found for: " + input).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().sendMessage("❌ Failed to load track: " + exception.getMessage()).queue();
                log("error", "Track load failed: ", exception);
            }
        });
    }

    private void showSearchResults(SlashCommandInteractionEvent event, List<AudioTrack> tracks, String query) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        long guildId = guild.getIdLong();

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack track = tracks.get(i);
            description.append(String.format("**%d.** [%s](%s) `%s`\n",
                    i + 1,
                    track.getInfo().title,
                    track.getInfo().uri,
                    formatDuration(track.getDuration())
            ));
        }
        description.append("\n💡 React with 1️⃣-5️⃣ to select a track");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔍 Search Results: " + truncate(query, 50))
                .setDescription(description.toString())
                .setThumbnail("https://puu.sh/KgdPy.gif")
                .setColor(Color.CYAN)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue(msg -> {
            searchMessages.put(guildId, msg.getIdLong());
            addNumberReactions(msg);
        });
    }

    private void handleSkipSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager manager = getGuildMusicManager(guild);
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            event.reply("❌ Nothing to skip!").setEphemeral(true).queue();
            return;
        }

        String skippedTitle = current.getInfo().title;

        // Save to history before skipping
        saveToHistory(guild.getIdLong(), getUserIdFromTrack(current), current);

        manager.scheduler.skip();

        event.reply("⏭️ Skipped: **" + skippedTitle + "**").queue();
    }

    private void handleQueueSlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Invalid subcommand!").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(guild);

        switch (subcommand) {
            case "view" -> {
                AudioTrack current = manager.player.getPlayingTrack();
                if (current == null && manager.scheduler.queue.isEmpty()) {
                    event.reply("📋 The queue is empty! Use `/play` to add tracks.").queue();
                    return;
                }

                StringBuilder queueText = new StringBuilder();
                if (current != null) {
                    queueText.append("**🎵 Now Playing:**\n")
                            .append(String.format("▶️ [%s](%s) `%s`\n\n",
                                    current.getInfo().title,
                                    current.getInfo().uri,
                                    formatDuration(current.getDuration())));
                }

                List<AudioTrack> upcoming = new ArrayList<>(manager.scheduler.queue);
                if (!upcoming.isEmpty()) {
                    queueText.append("**📋 Up Next:**\n");
                    int limit = Math.min(10, upcoming.size());
                    for (int i = 0; i < limit; i++) {
                        AudioTrack track = upcoming.get(i);
                        queueText.append(String.format("%d. [%s](%s) `%s`\n",
                                i + 1, track.getInfo().title, track.getInfo().uri,
                                formatDuration(track.getDuration())));
                    }

                    if (upcoming.size() > 10) {
                        queueText.append(String.format("\n*...and %d more tracks*", upcoming.size() - 10));
                    }
                }

                long totalDuration = (current != null ? current.getDuration() - current.getPosition() : 0)
                        + upcoming.stream().mapToLong(AudioTrack::getDuration).sum();

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📋 Music Queue")
                        .setDescription(queueText.toString())
                        .addField("Total Tracks", String.valueOf(manager.scheduler.queue.size() + (current != null ? 1 : 0)), true)
                        .addField("Total Duration", formatDuration(totalDuration), true)
                        .addField("Loop Mode", manager.scheduler.loopMode.name(), true)
                        .setColor(Color.BLUE)
                        .setTimestamp(Instant.now());

                event.replyEmbeds(embed.build()).queue();
            }
            case "clear" -> {
                if (!validateMusicControl(event)) return;
                int cleared = manager.scheduler.queue.size();
                manager.scheduler.queue.clear();
                clearQueueFromDatabase(guild.getIdLong());
                event.reply(String.format("🗑️ Cleared %d track(s) from the queue", cleared)).queue();
            }
            case "shuffle" -> {
                if (!validateMusicControl(event)) return;
                manager.scheduler.toggleShuffle();
                event.reply("🔀 Shuffle " + (manager.scheduler.shuffle ? "enabled" : "disabled")).queue();
            }
            case "remove" -> {
                if (!validateMusicControl(event)) return;

                OptionMapping posOption = event.getOption("position");
                if (posOption == null) {
                    event.reply("❌ Please provide a position!").setEphemeral(true).queue();
                    return;
                }

                int position = posOption.getAsInt();
                List<AudioTrack> queueList = new ArrayList<>(manager.scheduler.queue);

                if (position < 1 || position > queueList.size()) {
                    event.reply("❌ Invalid position! Queue has " + queueList.size() + " track(s).").setEphemeral(true).queue();
                    return;
                }

                AudioTrack removed = queueList.remove(position - 1);
                manager.scheduler.queue.clear();
                manager.scheduler.queue.addAll(queueList);

                event.reply(String.format("🗑️ Removed **%s** from position %d",
                        truncate(removed.getInfo().title, 50), position)).queue();
            }
            case "move" -> {
                if (!validateMusicControl(event)) return;

                OptionMapping fromOption = event.getOption("from");
                OptionMapping toOption = event.getOption("to");

                if (fromOption == null || toOption == null) {
                    event.reply("❌ Please provide both positions!").setEphemeral(true).queue();
                    return;
                }

                int fromPos = fromOption.getAsInt();
                int toPos = toOption.getAsInt();
                List<AudioTrack> queueList = new ArrayList<>(manager.scheduler.queue);
                int queueSize = queueList.size();

                if (fromPos < 1 || fromPos > queueSize || toPos < 1 || toPos > queueSize) {
                    event.reply("❌ Invalid position! Queue has " + queueSize + " track(s).").setEphemeral(true).queue();
                    return;
                }

                if (fromPos == toPos) {
                    event.reply("❌ From and to positions are the same!").setEphemeral(true).queue();
                    return;
                }

                AudioTrack track = queueList.remove(fromPos - 1);
                queueList.add(toPos - 1, track);
                manager.scheduler.queue.clear();
                manager.scheduler.queue.addAll(queueList);

                event.reply(String.format("↕️ Moved **%s** from position %d to %d",
                        truncate(track.getInfo().title, 50), fromPos, toPos)).queue();
            }
            default -> event.reply("❌ Unknown subcommand!").setEphemeral(true).queue();
        }
    }

    private void handleHistorySlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        OptionMapping limitOption = event.getOption("limit");
        int limit = limitOption != null ? limitOption.getAsInt() : 10;
        limit = Math.max(1, Math.min(25, limit));

        long guildId = guild.getIdLong();

        List<HistoryEntry> history = historyRepository.query()
                .whereGreaterThan("guild_id", guildId - 1)
                .whereLessThan("guild_id", guildId + 1)
                .orderByDesc("played_at")
                .limit(limit)
                .list();

        if (history.isEmpty()) {
            event.reply("📜 No music history found for this server.").queue();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            historyText.append(String.format("%d. **%s** by %s `%s`\n",
                    i + 1,
                    truncate(entry.trackTitle, 40),
                    truncate(entry.trackAuthor, 20),
                    formatDuration(entry.trackDuration)));
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📜 Music History")
                .setDescription(historyText.toString())
                .setColor(Color.ORANGE)
                .setFooter("Showing last " + history.size() + " tracks")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleVolumeSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        OptionMapping levelOption = event.getOption("level");
        if (levelOption == null) {
            event.reply("❌ Please provide a volume level!").setEphemeral(true).queue();
            return;
        }

        int volume = levelOption.getAsInt();
        if (volume < 0 || volume > 100) {
            event.reply("❌ Volume must be between 0 and 100!").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(guild);
        manager.player.setVolume(volume);
        event.reply(String.format("🔊 Volume set to %d%%", volume)).queue();
    }

    private void handleLeaveSlash(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        long guildId = guild.getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isConnected(guildId)) {
            event.reply("❌ Not connected to a voice channel!").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(guild);
        manager.scheduler.queue.clear();
        manager.player.stopTrack();
        clearQueueFromDatabase(guildId);

        vm.disconnect(guildId);

        event.reply("👋 Disconnected from voice channel and cleared queue").queue();
    }

    private void handleLoopSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager manager = getGuildMusicManager(guild);

        OptionMapping modeOption = event.getOption("mode");
        String modeStr = modeOption != null ? modeOption.getAsString().toLowerCase() : null;

        if (modeStr == null) {
            manager.scheduler.toggleLoop();
        } else {
            manager.scheduler.loopMode = switch (modeStr) {
                case "off" -> TrackScheduler.LoopMode.OFF;
                case "track", "one" -> TrackScheduler.LoopMode.ONE;
                case "queue", "all" -> TrackScheduler.LoopMode.QUEUE;
                default -> {
                    manager.scheduler.toggleLoop();
                    yield manager.scheduler.loopMode;
                }
            };
        }

        String emoji = switch (manager.scheduler.loopMode) {
            case OFF -> "➡️";
            case ONE -> "🔂";
            case QUEUE -> "🔁";
        };

        event.reply(emoji + " Loop mode: **" + manager.scheduler.loopMode.name() + "**").queue();
    }

    private void handlePauseSlash(SlashCommandInteractionEvent event) {
        if (!validateMusicControl(event)) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager manager = getGuildMusicManager(guild);
        boolean paused = !manager.player.isPaused();
        manager.player.setPaused(paused);

        event.reply(paused ? "⏸️ Paused playback" : "▶️ Resumed playback").queue();
    }

    // ==================== REACTION HANDLING ====================

    @EventHandler
    public void onReaction(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot() || !event.isFromGuild()) return;

        long guildId = event.getGuild().getIdLong();
        String emoji = event.getEmoji().getAsReactionCode();

        // Check if this is a search result message
        Long searchMsgId = searchMessages.get(guildId);
        if (searchMsgId != null && searchMsgId == event.getMessageIdLong()) {
            handleSearchReaction(event, emoji, event.getGuild());
            return;
        }

        // Check if this is a player control message
        Long playerMsgId = playerMessages.get(guildId);
        if (playerMsgId != null && playerMsgId == event.getMessageIdLong()) {
            handlePlayerReaction(event, emoji, event.getGuild());
        }
    }

    private void handleSearchReaction(MessageReactionAddEvent event, String emoji, Guild guild) {
        User user = event.getUser();
        if (user == null) return;

        event.getReaction().removeReaction(user).queue();

        List<AudioTrack> tracks = searchResults.get(guild.getIdLong());
        if (tracks == null) return;

        int index = getEmojiIndex(emoji);
        if (index >= 0 && index < tracks.size()) {
            AudioTrack selected = tracks.get(index);
            selected.setUserData(event.getUser());

            GuildMusicManager manager = getGuildMusicManager(guild);
            manager.scheduler.queue(selected);
            saveToQueue(guild.getIdLong(), event.getUser().getIdLong(), selected, manager.scheduler.queue.size());

            connectToVoiceFromEvent(event, guild);

            // Update the message
            event.retrieveMessage().queue(message -> message.editMessageEmbeds(
                    new EmbedBuilder()
                            .setTitle("✅ Track Added")
                            .setDescription(String.format("[%s](%s)", selected.getInfo().title, selected.getInfo().uri))
                            .setThumbnail(getTrackThumbnail(selected))
                            .setColor(Color.GREEN)
                            .setTimestamp(Instant.now())
                            .build()
            ).queue(msg -> {
                msg.clearReactions().queue();
                addPlayerReactions(msg);
                playerMessages.put(guild.getIdLong(), msg.getIdLong());
            }));

            searchMessages.remove(guild.getIdLong());
            searchResults.remove(guild.getIdLong());
        }
    }

    private void handlePlayerReaction(MessageReactionAddEvent event, String emoji, Guild guild) {
        User user = event.getUser();
        if (user == null) return;

        event.getReaction().removeReaction(user).queue();

        // Validate user is in voice channel
        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(guild);

        switch (emoji) {
            case "⏸️", "⏯️" -> {
                manager.player.setPaused(!manager.player.isPaused());
                updatePlayerEmbedRealtime(event, guild);
            }
            case "⏹️" -> {
                AudioTrack current = manager.player.getPlayingTrack();
                if (current != null) {
                    saveToHistory(guild.getIdLong(), getUserIdFromTrack(current), current);
                }
                manager.scheduler.queue.clear();
                manager.player.stopTrack();
                clearQueueFromDatabase(guild.getIdLong());
                getContext().getVoiceManager().disconnect(guild.getIdLong());
                updatePlayerMessage(event, "⏹️ Stopped", Color.RED);
            }
            case "⏭️" -> {
                AudioTrack current = manager.player.getPlayingTrack();
                if (current != null) {
                    saveToHistory(guild.getIdLong(), getUserIdFromTrack(current), current);
                }
                manager.scheduler.skip();
                updatePlayerEmbedRealtime(event, guild);
            }
            case "🔁" -> {
                manager.scheduler.toggleLoop();
                updatePlayerEmbedRealtime(event, guild);
            }
            case "🔀" -> {
                manager.scheduler.toggleShuffle();
                updatePlayerEmbedRealtime(event, guild);
            }
        }
    }

    private void updatePlayerEmbedRealtime(MessageReactionAddEvent event, Guild guild) {
        GuildMusicManager manager = getGuildMusicManager(guild);
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            updatePlayerMessage(event, "⏸️ No track playing", Color.GRAY);
            return;
        }

        EmbedBuilder embed = createNowPlayingEmbed(current, manager, event.getMember());

        event.retrieveMessage().queue(message -> message.editMessageEmbeds(embed.build()).queue());
    }

    private void updatePlayerMessage(MessageReactionAddEvent event, String message, Color color) {
        event.retrieveMessage().queue(msg -> msg.editMessageEmbeds(
                new EmbedBuilder()
                        .setDescription(message)
                        .setColor(color)
                        .setTimestamp(Instant.now())
                        .build()
        ).queue());
    }

    private int getEmojiIndex(String emoji) {
        return switch (emoji) {
            case "1️⃣" -> 0;
            case "2️⃣" -> 1;
            case "3️⃣" -> 2;
            case "4️⃣" -> 3;
            case "5️⃣" -> 4;
            default -> -1;
        };
    }

    // ==================== TEXT COMMAND HANDLERS (NP/NOWPLAYING ONLY) ====================

    private void handleNowPlayingText(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.reply("❌ This command can only be used in a server!");
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            ctx.getMessage().getChannel().sendMessageEmbeds(
                    new EmbedBuilder()
                            .setTitle("⏸️ No Track Playing")
                            .setDescription("The queue is empty. Use `/play <song>` to add music!")
                            .setColor(Color.GRAY)
                            .setThumbnail("https://puu.sh/KgLS9.gif")
                            .build()
            ).queue();
            return;
        }

        EmbedBuilder embed = createNowPlayingEmbed(current, manager, ctx.getMember());

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue(msg -> {
            playerMessages.put(ctx.getGuild().getIdLong(), msg.getIdLong());
            addPlayerReactions(msg);
        });
    }

    // ==================== UI HELPERS ====================

    private void addNumberReactions(Message msg) {
        String[] emojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣"};
        for (String emoji : emojis) {
            msg.addReaction(Emoji.fromUnicode(emoji)).queue();
        }
    }

    private void addPlayerReactions(Message msg) {
        String[] emojis = {"⏯️", "⏹️", "⏭️", "🔁", "🔀"};
        for (String emoji : emojis) {
            msg.addReaction(Emoji.fromUnicode(emoji)).queue();
        }
    }

    private EmbedBuilder createNowPlayingEmbed(AudioTrack track, GuildMusicManager manager, Member member) {
        String progressBar = createProgressBar(track);
        String loopEmoji = switch (manager.scheduler.loopMode) {
            case OFF -> "➡️";
            case ONE -> "🔂";
            case QUEUE -> "🔁";
        };

        // Member color (use default as color methods are deprecated)
        Color memberColor = Color.MAGENTA;

        return new EmbedBuilder()
                .setTitle("🎵 Now Playing")
                .setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri))
                .setThumbnail(getTrackThumbnail(track))
                .addField("Uploader", track.getInfo().author, true)
                .addField("Duration", formatDuration(track.getDuration()), true)
                .addField("👤 Requested By", "<@" + getUserIdFromTrack(track) + ">", true)
                .addField("Progress", progressBar, false)
                .addField("Mode", loopEmoji + " " + manager.scheduler.loopMode.name() +
                        (manager.scheduler.shuffle ? " | 🔀 Shuffle" : ""), true)
                .addField("📋 Queue", String.valueOf(manager.scheduler.queue.size()), true)
                .addField("🔊 Volume", manager.player.getVolume() + "%", true)
                .setColor(memberColor)
                .setFooter("Use reactions to control • ⏯️ ⏹️ ⏭️ 🔁 🔀")
                .setTimestamp(Instant.now());
    }

    // ==================== DATABASE OPERATIONS ====================

    private void saveToQueue(long guildId, long userId, AudioTrack track, int position) {
        try {
            QueueEntry entry = new QueueEntry();
            entry.id = System.currentTimeMillis();
            entry.guildId = guildId;
            entry.userId = userId;
            entry.trackUrl = track.getInfo().uri;
            entry.trackTitle = track.getInfo().title;
            entry.trackAuthor = track.getInfo().author;
            entry.trackDuration = track.getDuration();
            entry.position = position;
            entry.addedAt = Timestamp.from(Instant.now());

            queueRepository.save(entry);
        } catch (Exception e) {
            log("error", "Failed to save to queue database: " + e.getMessage());
        }
    }

    private void saveToHistory(long guildId, long userId, AudioTrack track) {
        try {
            HistoryEntry entry = new HistoryEntry();
            entry.id = System.currentTimeMillis();
            entry.guildId = guildId;
            entry.userId = userId;
            entry.trackUrl = track.getInfo().uri;
            entry.trackTitle = track.getInfo().title;
            entry.trackAuthor = track.getInfo().author;
            entry.trackDuration = track.getDuration();
            entry.playedAt = Timestamp.from(Instant.now());

            historyRepository.save(entry);
        } catch (Exception e) {
            log("error", "Failed to save to history database: " + e.getMessage());
        }
    }

    private void clearQueueFromDatabase(long guildId) {
        try {
            queueRepository.query()
                    .whereGreaterThan("guild_id", guildId - 1)
                    .whereLessThan("guild_id", guildId + 1)
                    .delete();
        } catch (Exception e) {
            log("error", "Failed to clear queue from database: " + e.getMessage());
        }
    }

    // ==================== GUILD MUSIC MANAGER ====================

    private GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), _ -> {
            GuildMusicManager manager = new GuildMusicManager(playerManager, guild, this);
            log("info", "Created music manager for guild: " + guild.getId());
            return manager;
        });
    }

    // ==================== VOICE CONNECTION ====================

    private void connectToVoice(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) return;

        if (voiceState.getChannel() == null) return;
        VoiceChannel channel = voiceState.getChannel().asVoiceChannel();

        Guild guild = event.getGuild();
        if (guild == null) return;

        long guildId = guild.getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isDAVEAvailable(guildId)) {
            event.getHook().sendMessage("⚠️ Voice encryption (DAVE) not available!").queue();
            return;
        }

        if (!vm.isConnected(guildId)) {
            vm.connect(guildId, channel.getIdLong()).thenAccept(status -> {
                if (status == VoiceConnectionStatus.CONNECTED) {
                    log("info", "Connected to voice: " + channel.getName());
                }
            });
        }
    }

    private void connectToVoiceFromEvent(MessageReactionAddEvent event, Guild guild) {
        Member member = event.getMember();
        if (member == null) return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) return;

        if (voiceState.getChannel() == null) return;
        VoiceChannel channel = voiceState.getChannel().asVoiceChannel();

        long guildId = guild.getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isConnected(guildId)) {
            vm.connect(guildId, channel.getIdLong());
        }
    }

    // ==================== VALIDATION HELPERS ====================

    private boolean validateMusicControl(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return false;
        }

        Member member = event.getMember();
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to control music!").setEphemeral(true).queue();
            return false;
        }

        return true;
    }

    // ==================== UTILITY METHODS ====================

    private String getTrackThumbnail(AudioTrack track) {
        String uri = track.getInfo().uri;
        Matcher matcher = Pattern.compile("v=([^&]+)").matcher(uri);
        if (matcher.find()) {
            return "https://img.youtube.com/vi/" + matcher.group(1) + "/maxresdefault.jpg";
        }
        return "https://puu.sh/KgqvW.gif";
    }

    private long getUserIdFromTrack(AudioTrack track) {
        Object userData = track.getUserData();
        if (userData instanceof User user) {
            return user.getIdLong();
        }
        return 0L;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String createProgressBar(AudioTrack track) {
        long pos = track.getPosition();
        long dur = track.getDuration();
        int totalBlocks = 14;

        int filledBlocks = 0;
        if (dur > 0) {
            filledBlocks = (int) ((double) pos / dur * totalBlocks);
        }

        filledBlocks = Math.max(0, Math.min(totalBlocks, filledBlocks));

        StringBuilder bar = new StringBuilder();
        bar.append(formatDuration(pos)).append(" ");

        for (int i = 0; i < totalBlocks; i++) {
            if (i == filledBlocks) {
                bar.append("🔘");
            } else {
                bar.append("▬");
            }
        }

        if (filledBlocks == totalBlocks) {
            bar.append("🔘");
        }

        bar.append(" ").append(formatDuration(dur));

        return bar.toString();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    // ==================== DATA CLASSES ====================

    public static class QueueEntry {
        public long id;
        public long guildId;
        public long userId;
        public String trackUrl;
        public String trackTitle;
        public String trackAuthor;
        public long trackDuration;
        public int position;
        public Timestamp addedAt;
    }

    public static class HistoryEntry {
        public long id;
        public long guildId;
        public long userId;
        public String trackUrl;
        public String trackTitle;
        public String trackAuthor;
        public long trackDuration;
        public Timestamp playedAt;
    }

    // ==================== GUILD MUSIC MANAGER CLASS ====================

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

    // ==================== TRACK SCHEDULER CLASS ====================

    private static class TrackScheduler extends AudioEventAdapter {
        public enum LoopMode {
            OFF,
            ONE,
            QUEUE
        }

        public final Queue<AudioTrack> queue = new LinkedList<>();
        private final Deque<AudioTrack> history = new ArrayDeque<>();

        public LoopMode loopMode = LoopMode.OFF;
        public boolean shuffle = false;

        public final AudioPlayer player;
        private final PudelMusicPlugin plugin;
        private final long guildId;

        public TrackScheduler(AudioPlayer player, PudelMusicPlugin plugin, long guildId) {
            this.player = player;
            this.plugin = plugin;
            this.guildId = guildId;
        }

        public void queue(AudioTrack track) {
            if (!player.startTrack(track, true)) {
                queue.offer(track);
            }
        }

        public void nextTrack(AudioTrack lastTrack) {
            if (lastTrack != null) {
                history.push(lastTrack);
                // Save to database history
                plugin.saveToHistory(guildId, plugin.getUserIdFromTrack(lastTrack), lastTrack);
            }

            if (queue.isEmpty()) {
                if (loopMode == LoopMode.QUEUE && !history.isEmpty()) {
                    while (!history.isEmpty()) {
                        queue.offer(history.removeLast().makeClone());
                    }
                } else {
                    return;
                }
            }

            if (queue.isEmpty()) return;

            AudioTrack nextTrack = shuffle ? pollRandom() : queue.poll();
            player.startTrack(nextTrack, false);
        }

        private AudioTrack pollRandom() {
            int index = new Random().nextInt(queue.size());
            int i = 0;
            for (AudioTrack track : queue) {
                if (i++ == index) {
                    queue.remove(track);
                    return track;
                }
            }
            return queue.poll();
        }

        public void skip() {
            nextTrack(player.getPlayingTrack());
        }

        public void toggleLoop() {
            loopMode = switch (loopMode) {
                case OFF -> LoopMode.ONE;
                case ONE -> LoopMode.QUEUE;
                case QUEUE -> LoopMode.OFF;
            };
        }

        public void toggleShuffle() {
            shuffle = !shuffle;
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            if (!endReason.mayStartNext) return;

            if (loopMode == LoopMode.ONE) {
                player.startTrack(track.makeClone(), false);
                return;
            }

            nextTrack(track);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onEnabled() {
        log("info", "PudelMusic plugin enabled with slash commands and database support!");
    }

    @Override
    protected void onDisabled() {
        // Cleanup: disconnect from all voice channels and stop all players
        for (Map.Entry<Long, GuildMusicManager> entry : musicManagers.entrySet()) {
            long guildId = entry.getKey();
            GuildMusicManager manager = entry.getValue();

            manager.scheduler.queue.clear();
            manager.player.stopTrack();
            manager.player.destroy();

            getContext().getVoiceManager().disconnect(guildId);
        }

        // Unregister all interactions
        getContext().getInteractionManager().unregisterAll(PLUGIN_ID);

        musicManagers.clear();
        searchResults.clear();
        playerMessages.clear();
        searchMessages.clear();

        if (playerManager != null) {
            playerManager.shutdown();
        }

        log("info", "PudelMusic plugin disabled and cleaned up");
    }
}
