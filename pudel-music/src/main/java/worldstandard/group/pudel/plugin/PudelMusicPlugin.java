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
package worldstandard.group.pudel.plugin;

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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import worldstandard.group.pudel.api.SimplePlugin;
import worldstandard.group.pudel.api.audio.VoiceConnectionStatus;
import worldstandard.group.pudel.api.audio.VoiceManager;
import worldstandard.group.pudel.api.command.CommandContext;
import worldstandard.group.pudel.api.event.EventHandler;
import worldstandard.group.pudel.api.event.Listener;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced Music Plugin for Pudel Discord Bot
 * <p>
 * Features:
 * - LavaPlayer integration with YouTube support
 * - Real-time embed updates via reactions
 * - Queue management with loop/shuffle
 * - Interactive reaction controls
 * - DAVE voice encryption support
 * - Multi-guild support
 *
 * @author Zazalng
 * @version 1.0.1
 */
public class PudelMusicPlugin extends SimplePlugin implements Listener {
    // ==================== STATE ENUMS ====================
    private enum CommandState {
        IDLE,       // No active command session
        SEARCHING,  // Waiting for search result selection
        PLAYER      // Player controls active
    }

    // ==================== STATE MANAGEMENT ====================
    private final Map<Long, List<AudioTrack>> searchResults = new ConcurrentHashMap<>();
    private final Map<Long, CommandState> guildStates = new ConcurrentHashMap<>();
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, Long> reactionMessages = new ConcurrentHashMap<>();
    private AudioPlayerManager playerManager;

    // ==================== PLUGIN INITIALIZATION ====================
    public PudelMusicPlugin() {
        super(
                "Pudel Music",
                "1.0.0",
                "Zazalng",
                "Advanced music playback with LavaPlayer, search, queue, and interactive controls"
        );
    }

    @Override
    protected void setup() {
        initializeLavaPlayer();

        command("play", this::handlePlay);
        command("p", this::handlePlay);

        command("skip", this::handleSkip);
        command("next", this::handleSkip);

        command("queue", this::handleQueue);
        command("q", this::handleQueue);

        command("nowplaying", this::handleNowPlaying);
        command("np", this::handleNowPlaying);

        command("volume", this::handleVolume);
        command("vol", this::handleVolume);

        command("clear", this::handleClear);

        command("leave", this::handleLeave);
        command("disconnect", this::handleLeave);

        // Register reaction listener
        listener(this);

        log("info", "PudelMusic plugin initialized with LavaPlayer");
    }

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev/", "", "Pudel v1.0.0");

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(ytk, new AndroidVr());

        this.playerManager.registerSourceManager(ytSourceManager);

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );

        log("info", "LavaPlayer initialized with YouTube support");
    }

    // ==================== GUILD MUSIC MANAGER ====================

    private GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), _ -> {
            GuildMusicManager manager = new GuildMusicManager(playerManager, guild);
            log("info", "Created music manager for guild: " + guild.getId());
            return manager;
        });
    }

    // ==================== PLAY COMMAND ====================

    private void handlePlay(CommandContext ctx) {
        if (!validateGuildContext(ctx)) return;

        long guildId = ctx.getGuild().getIdLong();
        CommandState state = getState(guildId);

        if (state == CommandState.SEARCHING && isNumeric(ctx.getArgsString())) {
            //Search with correct format
            handleSearchingState(ctx);
        } else {
            //Reset execute search and state
            executePlayCommand(ctx);
        }
    }

    private void executePlayCommand(CommandContext ctx) {
        String args = ctx.getArgsString();

        if (args.isEmpty()) {
            handleEmptyPlayCommand(ctx);
            return;
        }

        if (!validateUserVoiceState(ctx)) {
            sendNotInVoiceChannel(ctx);
            return;
        }

        queueSong(ctx, args);
    }

    private void handleEmptyPlayCommand(CommandContext ctx) {
        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());

        if (manager.player.getPlayingTrack() == null) {
            sendEmbed(ctx, "⏸️ No Track Playing",
                    "The queue is empty. Use `!play <song>` to add music!",
                    Color.GRAY, "https://puu.sh/KgLS9.gif");
        } else {
            sendCurrentTrack(ctx);
        }
    }

    private void queueSong(CommandContext ctx, String input) {
        boolean isUrl = input.startsWith("http://") || input.startsWith("https://");

        if (!isUrl) {
            // Search YouTube
            loadAndPlay(ctx, "ytsearch:" + input);
        } else {
            // Direct URL
            loadAndPlay(ctx, input);
        }
    }

    private void loadAndPlay(CommandContext ctx, String trackUrl) {
        long guildId = ctx.getGuild().getIdLong();
        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());

        playerManager.loadItem(trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(ctx.getUser());
                manager.scheduler.queue(track);

                EmbedBuilder embed = createEmbed(ctx)
                        .setTitle("✅ Added to Queue")
                        .setDescription(String.format("[%s](%s)", getTrackTitle(track), track.getInfo().uri))
                        .setThumbnail(getTrackThumbnail(track))
                        .addField("Uploader", track.getInfo().author, true)
                        .addField("Duration", formatDuration(track.getDuration()), true)
                        .addField("Position", String.valueOf(manager.scheduler.queue.size()), true)
                        .setColor(Color.GREEN);

                ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue();

                connectToVoice(ctx);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    // Show search results
                    List<AudioTrack> tracks = playlist.getTracks().subList(0, Math.min(5, playlist.getTracks().size()));
                    searchResults.put(guildId, tracks);
                    showSearchResults(ctx, tracks, trackUrl.substring(9)); // Remove "ytsearch:"
                } else {
                    // Load entire playlist
                    for (AudioTrack track : playlist.getTracks()) {
                        track.setUserData(ctx.getUser());
                        manager.scheduler.queue(track);
                    }

                    EmbedBuilder embed = createEmbed(ctx)
                            .setTitle("📋 Playlist Added")
                            .setDescription(String.format("**%s**", playlist.getName()))
                            .setThumbnail("https://puu.sh/KgxX3.gif")
                            .addField("Tracks", String.valueOf(playlist.getTracks().size()), true)
                            .setColor(Color.BLUE);

                    ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue();
                    connectToVoice(ctx);
                }
            }

            @Override
            public void noMatches() {
                ctx.reply("❌ No results found for: " + trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                ctx.reply("❌ Failed to load track: " + exception.getMessage());
                log("error", "Track load failed: ", exception);
            }
        });
    }

    private void showSearchResults(CommandContext ctx, List<AudioTrack> tracks, String query) {
        long guildId = ctx.getGuild().getIdLong();

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack track = tracks.get(i);
            description.append(String.format("[%d. %s - %s](%s) `%s`\n",
                    i + 1,
                    track.getInfo().title,
                    track.getInfo().author,
                    track.getInfo().uri,
                    formatDuration(track.getDuration())
            ));
        }
        description.append("\n💡 React with 1️⃣-5️⃣ to select a track");

        EmbedBuilder embed = createEmbed(ctx)
                .setTitle("🔍 Search Results: " + query)
                .setDescription(description.toString())
                .setThumbnail("https://puu.sh/KgdPy.gif")
                .setColor(Color.CYAN);

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            reactionMessages.put(guildId, message.getIdLong());
            addNumberReactions(message);
            setState(guildId, CommandState.SEARCHING);
        });
    }

    private void handleSearchingState(CommandContext ctx) {
        String args = ctx.getArgsString();
        long guildId = ctx.getGuild().getIdLong();

        if (!isNumeric(args)) {
            setState(guildId, CommandState.IDLE);
            ctx.reply("❌ Invalid selection. Search cancelled.");
            return;
        }

        int index = Integer.parseInt(args) - 1;
        List<AudioTrack> tracks = searchResults.get(guildId);

        if (tracks == null || index < 0 || index >= tracks.size()) {
            ctx.reply("❌ Invalid selection number!");
            setState(guildId, CommandState.IDLE);
            return;
        }

        AudioTrack selected = tracks.get(index);
        selected.setUserData(ctx.getUser());

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        manager.scheduler.queue(selected);

        EmbedBuilder embed = createEmbed(ctx)
                .setTitle("✅ Added to Queue")
                .setDescription(String.format("[%s](%s)", getTrackTitle(selected), selected.getInfo().uri))
                .setThumbnail(getTrackThumbnail(selected))
                .addField("Artist", selected.getInfo().author, true)
                .addField("Duration", formatDuration(selected.getDuration()), true)
                .setColor(Color.GREEN);

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue();

        connectToVoice(ctx);
        setState(guildId, CommandState.IDLE);
    }

    // ==================== NOW PLAYING COMMAND ====================

    private void handleNowPlaying(CommandContext ctx) {
        if (!validateGuildContext(ctx)) return;
        sendCurrentTrack(ctx);
    }

    private void sendCurrentTrack(CommandContext ctx) {
        long guildId = ctx.getGuild().getIdLong();
        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            sendEmbed(ctx, "⏸️ No Track Playing",
                    "The queue is empty. Use `!play <song>` to add music!",
                    Color.GRAY, "https://puu.sh/KgLS9.gif");
            return;
        }

        String progressBar = createProgressBar(current);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Now Playing")
                .setDescription(String.format("[%s](%s)", getTrackTitle(current), current.getInfo().uri))
                .setThumbnail(getTrackThumbnail(current))
                .addField("Uploader", current.getInfo().author, true)
                .addField("Duration", formatDuration(current.getDuration()), true)
                .addField("👤 Requested By", "<@" + getUserId(current) + ">", true)
                .addField("Progress", progressBar, false)
                .addField("Music Mode", manager.scheduler.getModeString(), true)
                .addField("📋 Queue", String.valueOf(manager.scheduler.queue.size()), true)
                .setColor(ctx.getMember() != null ? ctx.getMember().getColors().getPrimary() : null)
                .setTimestamp(Instant.now());

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            reactionMessages.put(guildId, message.getIdLong());
            addPlayerReactions(message);
            setState(guildId, CommandState.PLAYER);
        });
    }

    // ==================== QUEUE COMMAND ====================
    private void handleQueue(CommandContext ctx) {
        if (!validateGuildContext(ctx)) return;

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null && manager.scheduler.queue.isEmpty()) {
            ctx.reply("📋 The queue is empty! Use `!play <song>` to add tracks.");
            return;
        }

        StringBuilder queueText = new StringBuilder();

        if (current != null) {
            queueText.append("**🎵 Now Playing:**\n")
                    .append(String.format("▶️ [%s](%s) `%s`\n\n",
                            getTrackTitle(current), current.getInfo().uri, formatDuration(current.getDuration())));
        }

        List<AudioTrack> upcoming = new ArrayList<>(manager.scheduler.queue);
        if (!upcoming.isEmpty()) {
            queueText.append("**📋 Up Next:**\n");
            int limit = Math.min(10, upcoming.size());
            for (int i = 0; i < limit; i++) {
                AudioTrack track = upcoming.get(i);
                queueText.append(String.format("%d. [%s](%s) `%s`\n",
                        i + 1, getTrackTitle(track), track.getInfo().uri, formatDuration(track.getDuration())));
            }

            if (upcoming.size() > 10) {
                queueText.append(String.format("\n*...and %d more tracks*", upcoming.size() - 10));
            }
        }

        EmbedBuilder embed = createEmbed(ctx)
                .setTitle("📋 Music Queue")
                .setDescription(queueText.toString())
                .addField("Total", String.valueOf(manager.scheduler.queue.size() + (current != null ? 1 : 0)), true)
                .setColor(ctx.getMember().getColors().getPrimary());

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== CONTROL COMMANDS ====================
    private void handleSkip(CommandContext ctx) {
        if (!validateGuildContext(ctx) || !validateMusicControl(ctx)) return;

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            ctx.reply("❌ Nothing to skip!");
            return;
        }

        String skippedTitle = getTrackTitle(current);

        // CHANGE: Use .skip() instead of .nextTrack()
        // This ensures the current song is saved to history before the next one starts
        manager.scheduler.skip();

        sendEmbed(ctx, "⏭️ Skipped", "Skipped: " + skippedTitle, Color.YELLOW, null);
    }

    private void handleVolume(CommandContext ctx) {
        if (!validateGuildContext(ctx) || !validateMusicControl(ctx)) return;

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());

        if (!ctx.hasArgs()) {
            ctx.replyFormat("🔊 Current volume: %d%%\nUsage: `!volume <0-100>`", manager.player.getVolume());
            return;
        }

        try {
            int volume = Integer.parseInt(ctx.getArg(0));
            if (volume < 0 || volume > 100) {
                ctx.reply("❌ Volume must be between 0 and 100!");
                return;
            }
            manager.player.setVolume(volume);
            ctx.replyFormat("🔊 Volume set to %d%%", volume);
        } catch (NumberFormatException e) {
            ctx.reply("❌ Invalid number! Usage: `!volume <0-100>`");
        }
    }

    private void handleClear(CommandContext ctx) {
        if (!validateGuildContext(ctx) || !validateMusicControl(ctx)) return;

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        int cleared = manager.scheduler.queue.size();
        manager.scheduler.queue.clear();

        ctx.replyFormat("🗑️ Cleared %d track(s) from the queue", cleared);
    }

    private void handleLeave(CommandContext ctx) {
        if (!validateGuildContext(ctx) || !validateMusicControl(ctx)) return;

        long guildId = ctx.getGuild().getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isConnected(guildId)) {
            ctx.reply("❌ Not connected to a voice channel!");
            return;
        }

        GuildMusicManager manager = getGuildMusicManager(ctx.getGuild());
        manager.scheduler.queue.clear();
        manager.player.stopTrack();

        vm.disconnect(guildId);
        setState(guildId, CommandState.IDLE);

        sendEmbed(ctx, "👋 Disconnected", "Left voice channel and cleared queue", Color.GRAY, null);
    }

    // ==================== REACTION HANDLING ====================

    @EventHandler
    public void onReaction(MessageReactionAddEvent event) {
        if (event.getUser().isBot() || !event.isFromGuild()) return;

        long guildId = event.getGuild().getIdLong();
        Long messageId = reactionMessages.get(guildId);

        if (messageId == null || messageId != event.getMessageIdLong()) return;

        event.getReaction().removeReaction(event.getUser()).queue();

        CommandState state = getState(guildId);
        String emoji = event.getEmoji().getAsReactionCode();

        if (state == CommandState.SEARCHING) {
            handleSearchReaction(event, emoji, event.getGuild());
        } else if (state == CommandState.PLAYER) {
            handlePlayerReaction(event, emoji, event.getGuild());
        }
    }

    private void handleSearchReaction(MessageReactionAddEvent event, String emoji, Guild guild) {
        List<AudioTrack> tracks = searchResults.get(guild.getIdLong());
        if (tracks == null) return;

        int index = getEmojiIndex(emoji);
        if (index >= 0 && index < tracks.size()) {
            AudioTrack selected = tracks.get(index);
            selected.setUserData(event.getUser());

            GuildMusicManager manager = getGuildMusicManager(guild);
            manager.scheduler.queue(selected);
            connectToVoiceFromEvent(event, guild);
            setState(guild.getIdLong(), CommandState.IDLE);

            // Update the message
            event.retrieveMessage().queue(message -> message.editMessageEmbeds(
                    new EmbedBuilder()
                            .setTitle("✅ Track Added")
                            .setDescription(String.format("[%s](%s)", getTrackTitle(selected), selected.getInfo().uri))
                            .setColor(Color.GREEN)
                            .build()
            ).queue(message1 -> message1.clearReactions().queue()));
        }
    }

    private void handlePlayerReaction(MessageReactionAddEvent event, String emoji, Guild guild) {
        GuildMusicManager manager = getGuildMusicManager(guild);
        VoiceManager vm = getContext().getVoiceManager();

        switch (emoji) {
            case"⏸️":
                manager.player.setPaused(!manager.player.isPaused());
                updatePlayerEmbedRealtime(event, guild);
                break;
            case "⏹️": // Stop
                manager.scheduler.queue.clear();
                manager.player.stopTrack();
                vm.disconnect(guild.getIdLong());
                setState(guild.getIdLong(), CommandState.IDLE);
                updatePlayerMessage(event, "⏹️ Stopped", Color.RED);
                break;

            case "⏭️": // Skip
                manager.scheduler.skip();
                updatePlayerEmbedRealtime(event, guild);
                break;

            case "🔁": // Loop
                manager.scheduler.toggleLoop();
                updatePlayerEmbedRealtime(event, guild);
                break;

            case "🔀": // Shuffle
                manager.scheduler.toggleShuffle();
                updatePlayerEmbedRealtime(event, guild);
                break;

            case "❤️": // Favorite
                log("info", "Track favorited by user: " + event.getUser().getId());
                break;
        }
    }

    private void updatePlayerEmbedRealtime(MessageReactionAddEvent event, Guild guild) {
        GuildMusicManager manager = getGuildMusicManager(guild);
        AudioTrack current = manager.player.getPlayingTrack();

        if (current == null) {
            updatePlayerMessage(event, "⏸️ No track playing", Color.GRAY);
            return;
        }

        String progressBar = createProgressBar(current);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Now Playing")
                .setDescription(String.format("[%s](%s)", getTrackTitle(current), current.getInfo().uri))
                .setThumbnail(getTrackThumbnail(current))
                .addField("Uploader", current.getInfo().author, true)
                .addField("Duration", formatDuration(current.getDuration()), true)
                .addField("👤 Requested By", "<@" + getUserId(current) + ">", true)
                .addField("Progress", progressBar, false)
                .addField("Music Mode", manager.scheduler.getModeString(), true)
                .addField("📋 Queue", String.valueOf(manager.scheduler.queue.size()), true)
                .setColor(event.getMember() != null ? event.getMember().getColors().getPrimary() : null)
                .setTimestamp(Instant.now());

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

    private void updatePlayerEmbed(long guildId) {
        Long messageId = reactionMessages.get(guildId);
        if (messageId == null) return;

        // This would require storing the channel, skipping for now
        // In production, store both message ID and channel ID
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

    // ==================== HELPER METHODS ====================

    private void connectToVoice(CommandContext ctx) {
        Member member = ctx.getMember();
        if (member == null) return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) return;

        VoiceChannel channel = voiceState.getChannel().asVoiceChannel();
        long guildId = ctx.getGuild().getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isDAVEAvailable(guildId)) {
            ctx.reply("⚠️ Voice encryption (DAVE) not available!");
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

        VoiceChannel channel = voiceState.getChannel().asVoiceChannel();
        long guildId = guild.getIdLong();
        VoiceManager vm = getContext().getVoiceManager();

        if (!vm.isConnected(guildId)) {
            vm.connect(guildId, channel.getIdLong());
        }
    }

    private boolean validateGuildContext(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.reply("❌ This command can only be used in a server!");
            return false;
        }
        return true;
    }

    private boolean validateUserVoiceState(CommandContext ctx) {
        Member member = ctx.getMember();
        if (member == null) return false;

        GuildVoiceState state = member.getVoiceState();
        return state != null && state.inAudioChannel();
    }

    private boolean validateMusicControl(CommandContext ctx) {
        if (!validateUserVoiceState(ctx)) {
            sendEmbed(ctx, "❌ Not in Voice",
                    "You must be in a voice channel to control music!",
                    Color.RED, null);
            return false;
        }
        return true;
    }

    private void sendNotInVoiceChannel(CommandContext ctx) {
        sendEmbed(ctx, "❌ Not in Voice Channel",
                "You must be in a voice channel to play music!",
                Color.RED, "https://puu.sh/KgP67.gif");
    }

    private void sendEmbed(CommandContext ctx, String title, String description,
                           Color color, String thumbnail) {
        EmbedBuilder embed = createEmbed(ctx)
                .setTitle(title)
                .setDescription(description)
                .setColor(color);

        if (thumbnail != null) {
            embed.setThumbnail(thumbnail);
        }

        ctx.getMessage().getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private EmbedBuilder createEmbed(CommandContext ctx) {
        return new EmbedBuilder()
                .setFooter("Requested by " + ctx.getUser().getName(),
                        ctx.getUser().getAvatarUrl())
                .setTimestamp(Instant.now());
    }

    private void addNumberReactions(Message msg) {
        String[] emojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣"};
        for (String emoji : emojis) {
            msg.addReaction(Emoji.fromUnicode(emoji)).queue();
        }
    }

    private void addPlayerReactions(Message msg) {
        String[] emojis = {"⏸️", "⏹️", "⏭️", "🔁", "🔀", "❤️"};
        for (String emoji : emojis) {
            msg.addReaction(Emoji.fromUnicode(emoji)).queue();
        }
    }

    // ==================== TRACK UTILITY METHODS ====================

    private String getTrackTitle(AudioTrack track) {
        return track.getInfo().title;
    }

    private String getTrackThumbnail(AudioTrack track) {
        String uri = track.getInfo().uri;
        Matcher matcher = Pattern.compile("v=([^&]+)").matcher(uri);
        if (matcher.find()) {
            return "https://img.youtube.com/vi/" + matcher.group(1) + "/maxresdefault.jpg";
        }
        return "https://puu.sh/KgqvW.gif";
    }

    private String getUserId(AudioTrack track) {
        Object userData = track.getUserData();
        if (userData instanceof User) {
            return ((User) userData).getId();
        }
        return "Unknown";
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

        // Calculate how many blocks should be filled
        // (Current Position / Total Duration) * Total Blocks
        int filledBlocks = 0;
        if (dur > 0) {
            filledBlocks = (int) ((double) pos / dur * totalBlocks);
        }

        // Ensure filledBlocks stays within bounds [0, totalBlocks]
        filledBlocks = Math.max(0, Math.min(totalBlocks, filledBlocks));

        StringBuilder bar = new StringBuilder();
        bar.append(formatDuration(pos)).append(" ");

        // Build the bar
        for (int i = 0; i < totalBlocks; i++) {
            if (i == filledBlocks) {
                bar.append("🔘");
            } else {
                bar.append("▬");
            }
        }

        // If the song is at the very end, make sure the button doesn't disappear
        if (filledBlocks == totalBlocks) {
            bar.append("🔘");
        }

        bar.append(" ").append(formatDuration(dur));

        return bar.toString();
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private CommandState getState(long guildId) {
        return guildStates.getOrDefault(guildId, CommandState.IDLE);
    }

    private void setState(long guildId, CommandState state) {
        guildStates.put(guildId, state);
    }

    // ==================== GUILD MUSIC MANAGER CLASS ====================

    private static class GuildMusicManager {
        public final AudioPlayer player;
        public final TrackScheduler scheduler;

        public GuildMusicManager(AudioPlayerManager manager, Guild g) {
            player = manager.createPlayer();
            scheduler = new TrackScheduler(player);
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
        private final Queue<AudioTrack> queue = new LinkedList<>();
        // Standard history stack
        private final Deque<AudioTrack> history = new ArrayDeque<>();

        private LoopMode loopMode = LoopMode.OFF;
        private boolean shuffle = false;

        public final AudioPlayer player;

        public TrackScheduler(AudioPlayer player) {
            this.player = player;
        }

        public void queue(AudioTrack track) {
            if (!player.startTrack(track, true)) {
                queue.offer(track);
            }
        }

        // UPDATED: Accept the previous track explicitly
        public void nextTrack(AudioTrack lastTrack) {
            // If a track just finished, save it to history
            if (lastTrack != null) {
                history.push(lastTrack);
            }

            if (queue.isEmpty()) {
                if (loopMode == LoopMode.QUEUE && !history.isEmpty()) {
                    // Refill queue from history
                    while (!history.isEmpty()) {
                        // clone() is required because the old track object is "dead"
                        queue.offer(history.removeLast().makeClone());
                    }
                } else {
                    // Queue is empty and no loop
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
            // Pass the currently playing track to history before skipping
            nextTrack(player.getPlayingTrack());
        }

        public void toggleLoop(){
            loopMode = switch(loopMode){
                case OFF -> LoopMode.ONE;
                case ONE -> LoopMode.QUEUE;
                case QUEUE -> LoopMode.OFF;
            };
        }

        public void toggleShuffle() {
            shuffle = !shuffle;
        }

        public String getModeString() {
            return "Loop: " + loopMode.name() + ", Shuffle: " + shuffle;
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            if (!endReason.mayStartNext) return;

            if (loopMode == LoopMode.ONE) {
                player.startTrack(track.makeClone(), false);
                return;
            }

            // FIX: Pass the track that just ended so it gets added to history
            nextTrack(track);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onEnabled() {
        log("info", "PudelMusic plugin enabled with LavaPlayer!");
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

        musicManagers.clear();
        searchResults.clear();
        guildStates.clear();
        reactionMessages.clear();

        if (playerManager != null) {
            playerManager.shutdown();
        }

        log("info", "PudelMusic plugin disabled and cleaned up");
    }
}
