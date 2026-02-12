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
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.database.*;
import group.worldstandard.pudel.api.event.EventHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        name = "Pudel's Music",
        version = "2.2.0",
        author = "Zazalng",
        description = "Database-backed Music Queue with advanced controls"
)
public class PudelMusicPlugin {

    private PluginContext context;
    private PluginDatabaseManager db;
    private AudioPlayerManager playerManager;

    // Repositories
    private PluginRepository<QueueEntry> queueRepo;
    private PluginRepository<HistoryEntry> historyRepo;

    // Runtime Cache
    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<String, List<AudioTrack>> searchCache = new ConcurrentHashMap<>();

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        this.db = ctx.getDatabaseManager();

        initializeDatabase();
        initializeLavaPlayer();

        ctx.log("info", "Music Plugin Loaded. DB & Audio System Ready.");
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        // Clean up players
        musicManagers.values().forEach(m -> m.player.destroy());
        playerManager.shutdown();
        return true;
    }

    // =========================================================================
    // 1. Database & Initialization
    // =========================================================================

    private void initializeDatabase() {
        // Queue Table: Stores the current state of the playlist
        TableSchema queueSchema = TableSchema.builder("music_queue")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_blob", ColumnType.TEXT, false) // Base64 encoded track
                .column("status", ColumnType.STRING, 20, false, "'QUEUE'") // QUEUE, PLAYED, CURRENT
                .column("title", ColumnType.STRING, 255, true) // For display/debug
                .column("is_looped", ColumnType.BOOLEAN, false, "false")
                .index("guild_id")
                .build();

        db.createTable(queueSchema);

        // History Table: Immutable log
        TableSchema historySchema = TableSchema.builder("music_history")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_title", ColumnType.STRING, 255, false)
                .column("track_url", ColumnType.TEXT, false)
                .column("played_at", ColumnType.BIGINT, false)
                .index("guild_id")
                .build();

        db.createTable(historySchema);

        this.queueRepo = db.getRepository("music_queue", QueueEntry.class);
        this.historyRepo = db.getRepository("music_history", HistoryEntry.class);
    }

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev/", "", "Pudel v2.0.0-rc5");

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(
                ytk,
                new WebWithThumbnail(),
                new MWebWithThumbnail(),
                new AndroidMusicWithThumbnail(),
                new AndroidVrWithThumbnail(),
                new Tv()
        );

        ytSourceManager.useOauth2("", true);
        this.playerManager.registerSourceManager(ytSourceManager);

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
    }

    // =========================================================================
    // 2. Command Handlers
    // =========================================================================

    @SlashCommand(name = "play", description = "Play a song or search", options = {
            @CommandOption(name = "query", description = "URL or Search Term", required = true)
    })
    public void onPlay(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        String query = event.getOption("query").getAsString();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null || !member.getVoiceState().inAudioChannel()) {
            event.getHook().editOriginal("❌ You must be in a voice channel.").queue();
            return;
        }

        GuildMusicManager musicManager = getGuildAudioPlayer(guild);

        if (!guild.getAudioManager().isConnected()) {
            guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
        }

        String searchPrefix = query.startsWith("http") ? "" : "ytsearch:";

        playerManager.loadItemOrdered(musicManager, searchPrefix + query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track, member.getIdLong());
                event.getHook().editOriginal("✅ Added to queue: **" + track.getInfo().title + "**").queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    handleSearchResults(event, playlist);
                } else {
                    for (AudioTrack track : playlist.getTracks()) {
                        musicManager.scheduler.queue(track, member.getIdLong());
                    }
                    event.getHook().editOriginal("✅ Added **" + playlist.getTracks().size() + "** tracks from playlist.").queue();
                }
            }

            @Override
            public void noMatches() {
                event.getHook().editOriginal("❌ No matches found.").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().editOriginal("❌ Load failed: " + exception.getMessage()).queue();
            }
        });
    }

    @SlashCommand(name = "np", description = "Show Music Controller")
    public void onNowPlaying(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager mgr = getGuildAudioPlayer(guild);
        AudioTrack current = mgr.player.getPlayingTrack();

        if (current == null) {
            event.reply("⏹️ Nothing is playing.").setEphemeral(true).queue();
            return;
        }

        sendController(event, mgr, current);
    }

    // --- NEW: Queue Manipulation (Requirement 5) ---

    @SlashCommand(
            name = "queue",
            description = "Manage the music queue",
            subcommands = {
                    @Subcommand(name = "view", description = "View upcoming songs"),
                    @Subcommand(name = "remove", description = "Remove a song from the queue")
            }
    )
    public void onQueue(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        Guild guild = event.getGuild();
        if (guild == null) return;

        if ("view".equals(subcommand)) {
            handleQueueView(event, guild);
        } else if ("remove".equals(subcommand)) {
            handleQueueRemove(event, guild);
        }
    }

    private void handleQueueView(SlashCommandInteractionEvent event, Guild guild) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", guild.getIdLong())
                .where("status", "QUEUE")
                .limit(20) // Limit display
                .list();

        if (queue.isEmpty()) {
            event.reply("EMPTY QUEUE").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🎵 Upcoming Queue");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < queue.size(); i++) {
            sb.append(String.format("`%d.` %s\n", i + 1, queue.get(i).getTitle()));
        }

        eb.setDescription(sb.toString());
        eb.setFooter("Total: " + queue.size());
        eb.setColor(Color.ORANGE);

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleQueueRemove(SlashCommandInteractionEvent event, Guild guild) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", guild.getIdLong())
                .where("status", "QUEUE")
                .limit(25) // Max select menu options
                .list();

        if (queue.isEmpty()) {
            event.reply("ℹ️ Queue is empty, nothing to remove.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create("music:remove:" + guild.getId());
        menu.setPlaceholder("Select a song to remove...");

        for (int i = 0; i < queue.size(); i++) {
            QueueEntry entry = queue.get(i);
            String label = (i + 1) + ". " + entry.getTitle();
            // Truncate label if too long for Discord API
            if (label.length() > 100) label = label.substring(0, 97) + "...";

            // Value is the Database ID
            menu.addOption(label, String.valueOf(entry.getId()));
        }

        event.reply("🗑️ **Select a song to remove:**")
                .setComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    // --- NEW: History Viewing (Requirement 6) ---

    @SlashCommand(name = "history", description = "View recently played songs")
    public void onHistory(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        List<HistoryEntry> history = historyRepo.query()
                .where("guild_id", guild.getIdLong())
                .orderByDesc("played_at")
                .limit(10)
                .list();

        if (history.isEmpty()) {
            event.reply("ℹ️ No history found.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📜 Recent History");
        eb.setColor(Color.GRAY);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

        for (HistoryEntry h : history) {
            String time = sdf.format(new Date(h.getPlayedAt()));
            eb.appendDescription(String.format("`[%s]` [%s](%s)\n", time, h.getTrackTitle(), h.getTrackUrl()));
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // =========================================================================
    // 3. Interaction Handlers
    // =========================================================================

    @SelectMenuHandler("music:remove:")
    public void onQueueRemoveSelect(StringSelectInteractionEvent event) {
        String dbIdStr = event.getValues().getFirst();
        long dbId = Long.parseLong(dbIdStr);

        // Verify it belongs to this guild for security
        Optional<QueueEntry> entryOpt = queueRepo.findById(dbId);

        if (entryOpt.isPresent() && entryOpt.get().getGuildId() == event.getGuild().getIdLong()) {
            queueRepo.deleteById(dbId);
            event.editMessage("✅ Removed **" + entryOpt.get().getTitle() + "** from the queue.")
                    .setComponents() // Remove the menu
                    .queue();
        } else {
            event.editMessage("❌ Track not found or already played.")
                    .setComponents()
                    .queue();
        }
    }

    @SelectMenuHandler("music:select:")
    public void onSearchSelect(StringSelectInteractionEvent event) {
        String searchId = event.getComponentId().split(":")[2];
        List<AudioTrack> tracks = searchCache.get(searchId);

        if (tracks == null) {
            event.reply("❌ Search expired.").setEphemeral(true).queue();
            return;
        }

        int index = Integer.parseInt(event.getValues().getFirst());
        AudioTrack selected = tracks.get(index);

        GuildMusicManager mgr = getGuildAudioPlayer(event.getGuild());
        mgr.scheduler.queue(selected, event.getUser().getIdLong());

        event.editMessage("✅ Queued: **" + selected.getInfo().title + "**")
                .setComponents()
                .queue();

        searchCache.remove(searchId);
    }

    @ButtonHandler("music:ctrl:")
    public void onControllerButton(ButtonInteractionEvent event) {
        String action = event.getComponentId().split(":")[2];
        GuildMusicManager mgr = getGuildAudioPlayer(event.getGuild());

        switch (action) {
            case "pause" -> {
                boolean paused = !mgr.player.isPaused();
                mgr.player.setPaused(paused);
            }
            case "skip" -> mgr.scheduler.nextTrack();
            case "loop" -> mgr.scheduler.cycleLoopMode();
            case "shuffle" -> mgr.scheduler.toggleShuffle();
        }

        AudioTrack track = mgr.player.getPlayingTrack();
        if (track != null) {
            event.editMessageEmbeds(buildControllerEmbed(mgr, track).build())
                    .setComponents(ActionRow.of(buildControllerButtons(mgr)))
                    .queue();
        } else {
            event.reply("⏹️ Queue finished.").setEphemeral(true).queue();
        }
    }

    private void handleSearchResults(SlashCommandInteractionEvent event, AudioPlaylist playlist) {
        List<AudioTrack> tracks = playlist.getTracks().subList(0, Math.min(5, playlist.getTracks().size()));
        String searchId = UUID.randomUUID().toString();
        searchCache.put(searchId, tracks);

        StringSelectMenu.Builder menu = StringSelectMenu.create("music:select:" + searchId)
                .setPlaceholder("Select a track to play...");

        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            menu.addOption(
                    (i + 1) + ". " + t.getInfo().title.substring(0, Math.min(90, t.getInfo().title.length())),
                    String.valueOf(i),
                    t.getInfo().author
            );
        }

        event.getHook().editOriginal("🔍 **Search Results:**")
                .setComponents(ActionRow.of(menu.build()))
                .queue();
    }

    // =========================================================================
    // 4. Logic & Scheduler
    // =========================================================================

    private void sendController(SlashCommandInteractionEvent event, GuildMusicManager mgr, AudioTrack track) {
        event.replyEmbeds(buildControllerEmbed(mgr, track).build())
                .setEphemeral(true)
                .addComponents(ActionRow.of(buildControllerButtons(mgr)))
                .queue();
    }

    private EmbedBuilder buildControllerEmbed(GuildMusicManager mgr, AudioTrack track) {
        String loopStatus = switch(mgr.scheduler.loopMode) {
            case 0 -> "Off ➡";
            case 1 -> "Queue 🔁";
            case 2 -> "Track 🔂";
            default -> "?";
        };

        String shuffleStatus = mgr.scheduler.shuffle ? "On 🔀" : "Off ➡";
        long duration = track.getDuration();
        long position = track.getPosition();

        return new EmbedBuilder()
                .setTitle("🎵 Now Playing")
                .setDescription("**[" + track.getInfo().title + "](" + track.getInfo().uri + ")**")
                .setThumbnail(track.getInfo().artworkUrl)
                .addField("Uploader", track.getInfo().author, true)

                .addField("Loop", loopStatus, true)
                .addField("Shuffle", shuffleStatus, true)
                .addField("Time", formatTime(position) + " / " + formatTime(duration), false)
                .setColor(Color.CYAN);
    }

    private List<Button> buildControllerButtons(GuildMusicManager mgr) {
        boolean isPaused = mgr.player.isPaused();
        return List.of(
                Button.primary("music:ctrl:pause", isPaused ? "▶ Resume" : "⏸ Pause"),
                Button.secondary("music:ctrl:skip", "⏭ Skip"),
                Button.secondary("music:ctrl:loop", "Loop: " + (mgr.scheduler.loopMode == 0 ? "Off" : (mgr.scheduler.loopMode == 1 ? "Queue" : "Track"))),
                Button.secondary("music:ctrl:shuffle", "Shuffle: " + (mgr.scheduler.shuffle ? "On" : "Off"))
        );
    }

    @EventHandler
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        // Requirement 7: Graceful Cleanup
        if (event.getChannelLeft() != null) {
            Guild guild = event.getGuild();
            if (event.getMember().getUser().getIdLong() == guild.getSelfMember().getUser().getIdLong()) {
                // Bot left/kicked
                GuildMusicManager mgr = musicManagers.get(guild.getIdLong());
                if (mgr != null) {
                    mgr.player.destroy();
                    mgr.scheduler.clearQueue(); // Clear DB
                    musicManagers.remove(guild.getIdLong());
                }
            }
        }
    }

    // =========================================================================
    // Inner Classes: Scheduler & Context
    // =========================================================================

    private GuildMusicManager getGuildAudioPlayer(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), k -> {
            GuildMusicManager mgr = new GuildMusicManager(playerManager, guild.getIdLong());
            guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(mgr.player));
            return mgr;
        });
    }

    public class GuildMusicManager {
        public final AudioPlayer player;
        public final TrackScheduler scheduler;

        public GuildMusicManager(AudioPlayerManager manager, long guildId) {
            this.player = manager.createPlayer();
            this.scheduler = new TrackScheduler(player, guildId);
            this.player.addListener(scheduler);
        }
    }

    public class TrackScheduler extends AudioEventAdapter {
        private final AudioPlayer player;
        private final long guildId;

        // 0 = Off, 1 = Loop Queue, 2 = Loop Track
        public int loopMode = 0;
        public boolean shuffle = false;

        public TrackScheduler(AudioPlayer player, long guildId) {
            this.player = player;
            this.guildId = guildId;
        }

        public void queue(AudioTrack track, long userId) {
            try {
                QueueEntry entry = new QueueEntry();
                entry.setGuildId(guildId);
                entry.setUserId(userId);
                entry.setStatus("QUEUE");
                entry.setTitle(track.getInfo().title);
                entry.setTrackBlob(encodeTrack(track));
                entry.setIsLooped(false);
                queueRepo.save(entry);

                if (player.getPlayingTrack() == null) {
                    nextTrack();
                }
            } catch (IOException e) {
                context.log("error", "Failed to encode track: " + e.getMessage());
            }
        }

        public void nextTrack() {
            // 1. Process the track that just finished (move CURRENT -> PLAYED)
            List<QueueEntry> active = queueRepo.query()
                    .where("guild_id", guildId)
                    .where("status", "CURRENT")
                    .list();

            for (QueueEntry e : active) {
                e.setStatus("PLAYED");
                queueRepo.save(e);

                // --- HISTORY CHECK ---
                // Only save to history if this wasn't a looped playback
                if (e.getIsLooped() == null || !e.getIsLooped()) {
                    try {
                        AudioTrack infoTrack = decodeTrack(e.getTrackBlob());
                        HistoryEntry hist = new HistoryEntry();
                        hist.setGuildId(guildId);
                        hist.setUserId(e.getUserId());
                        hist.setTrackTitle(e.getTitle());
                        hist.setTrackUrl(infoTrack.getInfo().uri);
                        hist.setPlayedAt(System.currentTimeMillis());
                        historyRepo.save(hist);
                    } catch (IOException ex) {
                        context.log("error", "History save failed: " + ex.getMessage());
                    }
                }
            }

            // 2. Fetch Next Track
            QueueEntry nextEntry = null;
            QueryBuilder<QueueEntry> query = queueRepo.query()
                    .where("guild_id", guildId)
                    .where("status", "QUEUE");

            // ... Shuffle logic (same as before) ...
            if (shuffle) {
                List<QueueEntry> candidates = query.list();
                if (!candidates.isEmpty()) {
                    nextEntry = candidates.get(new Random().nextInt(candidates.size()));
                }
            } else {
                List<QueueEntry> list = query.limit(1).list();
                if (!list.isEmpty()) nextEntry = list.getFirst();
            }

            // 3. Handle Loop Queue (Recycle PLAYED -> QUEUE)
            if (nextEntry == null && loopMode == 1) {
                List<QueueEntry> played = queueRepo.query()
                        .where("guild_id", guildId)
                        .where("status", "PLAYED")
                        .list();

                if (!played.isEmpty()) {
                    for (QueueEntry e : played) {
                        e.setStatus("QUEUE");
                        e.setIsLooped(true); // <--- Mark as Recycled (No History next time)
                        queueRepo.save(e);
                    }
                    nextTrack(); // Recursive call to pick up the recycled tracks
                    return;
                }
            }

            // 4. Play
            if (nextEntry != null) {
                try {
                    AudioTrack track = decodeTrack(nextEntry.getTrackBlob());
                    nextEntry.setStatus("CURRENT");
                    queueRepo.save(nextEntry);
                    track.setUserData(nextEntry.getId());
                    player.startTrack(track, false);
                } catch (IOException e) {
                    nextEntry.setStatus("ERROR");
                    queueRepo.save(nextEntry);
                    nextTrack();
                }
            } else {
                player.stopTrack();
            }
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
            if (reason.mayStartNext) {
                if (loopMode == 2) {
                    AudioTrack clone = track.makeClone();
                    clone.setUserData(track.getUserData());
                    player.startTrack(clone, false);
                } else {
                    nextTrack();
                }
            }
        }

        public void cycleLoopMode() {
            loopMode = (loopMode + 1) % 3;
        }

        public void toggleShuffle() {
            shuffle = !shuffle;
        }

        public void clearQueue() {
            queueRepo.deleteBy("guild_id", guildId);
        }
    }

    // =========================================================================
    // Utilities & Entities
    // =========================================================================

    private String encodeTrack(AudioTrack track) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        playerManager.encodeTrack(new MessageOutput(output), track);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private AudioTrack decodeTrack(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        return playerManager.decodeTrack(new MessageInput(input)).decodedTrack;
    }

    private String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Entity
    public static class QueueEntry {
        private Long id;
        private Long guildId;
        private Long userId;
        private String trackBlob;
        private String status;
        private String title;
        private Boolean isLooped;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGuildId() { return guildId; }
        public void setGuildId(Long guildId) { this.guildId = guildId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getTrackBlob() { return trackBlob; }
        public void setTrackBlob(String trackBlob) { this.trackBlob = trackBlob; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Boolean getIsLooped() { return isLooped; }
        public void setIsLooped(Boolean looped) { isLooped = looped; }
    }

    @Entity
    public static class HistoryEntry {
        private Long id;
        private Long guildId;
        private Long userId;
        private String trackTitle;
        private String trackUrl;
        private Long playedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGuildId() { return guildId; }
        public void setGuildId(Long guildId) { this.guildId = guildId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getTrackTitle() { return trackTitle; }
        public void setTrackTitle(String trackTitle) { this.trackTitle = trackTitle; }
        public String getTrackUrl() { return trackUrl; }
        public void setTrackUrl(String trackUrl) { this.trackUrl = trackUrl; }
        public Long getPlayedAt() { return playedAt; }
        public void setPlayedAt(Long playedAt) { this.playedAt = playedAt; }
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
}