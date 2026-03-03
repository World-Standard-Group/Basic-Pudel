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
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Unified Music Plugin for Pudel Discord Bot
 * <p>
 * Single {@code /music} command opens a Components v2 "Music Box" with:
 * <ul>
 *   <li>Now-playing display with artwork, progress, loop/shuffle status</li>
 *   <li>Playback controls: Pause, Skip, Loop, Shuffle</li>
 *   <li>Navigation: Queue Song, Queue View, History</li>
 *   <li>Paginated queue and history views with manipulation buttons</li>
 * </ul>
 *
 * @author Zazalng
 * @since 3.0.0
 */
@Plugin(
        name = "Pudel's Music",
        version = "3.0.1",
        author = "Zazalng",
        description = "Unified Music Box with Components v2"
)
public class PudelMusicPlugin {

    // ==================== CONSTANTS ====================
    private static final String BTN = "music:";
    private static final String MODAL_PREFIX = "music:modal:";
    private static final String MENU_PREFIX = "music:menu:";

    private static final int PAGE_SIZE = 10;
    private static final Color ACCENT_PLAYING = new Color(0x00D4AA);
    private static final Color ACCENT_IDLE = new Color(0x2B2D31);
    private static final Color ACCENT_QUEUE = new Color(0xFFA500);
    private static final Color ACCENT_HISTORY = new Color(0x808080);

    // ==================== STATE ====================
    private PluginContext context;
    private PluginDatabaseManager db;
    private AudioPlayerManager playerManager;

    private PluginRepository<QueueEntry> queueRepo;
    private PluginRepository<HistoryEntry> historyRepo;

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, MusicSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, List<AudioTrack>> searchCache = new ConcurrentHashMap<>();

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        this.db = ctx.getDatabaseManager();
        initializeDatabase();
        initializeLavaPlayer();
        ctx.log("info", "Music Plugin v3.0.0 loaded (Components v2)");
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        musicManagers.values().forEach(m -> m.player.destroy());
        playerManager.shutdown();
        return true;
    }

    // ==================== DATABASE ====================

    private void initializeDatabase() {
        TableSchema queueSchema = TableSchema.builder("music_queue")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_blob", ColumnType.TEXT, false)
                .column("status", ColumnType.STRING, 20, false, "'QUEUE'")
                .column("title", ColumnType.STRING, 255, true)
                .column("is_looped", ColumnType.BOOLEAN, false, "false")
                .index("guild_id")
                .build();
        db.createTable(queueSchema);

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

    // ==================== LAVAPLAYER ====================

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        /*String[] potoken = PotokenGenerator.generate();
        context.log("info", "Po-Token result are ".concat(Arrays.toString(potoken)));
        WebWithThumbnail.setPoTokenAndVisitorData(potoken[0], potoken[1]);
        MWebWithThumbnail.setPoTokenAndVisitorData(potoken[0], potoken[1]);
        WebEmbeddedWithThumbnail.setPoTokenAndVisitorData(potoken[0], potoken[1]);*/

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev/", "", context.getPudel().getUserAgent());

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(
                ytk,
                new MusicWithThumbnail(),
                new WebWithThumbnail(),
                new MWebWithThumbnail(),
                new WebEmbeddedWithThumbnail(),
                new AndroidMusicWithThumbnail(),
                new AndroidVrWithThumbnail(),
                new IosWithThumbnail(),
                new Tv(),
                new TvHtml5SimplyWithThumbnail()
        );

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );

        /* For current version of LavaPlayer / YoutubeSource
         * every client are require to sign in token, Which mean
         * all client in this list is NOT capable to play any song unless TV client
         *
         * Without oauth2Api currently no song can be play
         */
        String oauth2Api = "";
        ytSourceManager.useOauth2(oauth2Api, !oauth2Api.isEmpty());

        this.playerManager.registerSourceManager(ytSourceManager);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(name = "music", description = "Open the Music Box")
    public void onMusic(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        long userId = event.getUser().getIdLong();

        // Clean old session
        MusicSession old = activeSessions.get(userId);
        if (old != null) {
            old.cleanupTemp();
            if (old.message != null) {
                try{
                    old.message.delete().queue();
                } catch (IllegalStateException _){
                    context.log("warn", "message_id '".concat(old.message.getId()).concat("' may long gone. Skip delete."));
                }
            }
        }

        MusicSession session = new MusicSession(userId, guild.getIdLong());
        activeSessions.put(userId, session);

        GuildMusicManager mgr = getGuildAudioPlayer(guild);

        // Resume from stale queue if nothing is playing
        if (mgr.player.getPlayingTrack() == null) {
            recoverStaleQueue(guild.getIdLong());

            // Auto-join voice and start playback if queue has entries
            boolean hasQueue = !queueRepo.query()
                    .where("guild_id", guild.getIdLong())
                    .where("status", "QUEUE")
                    .limit(1).list().isEmpty();

            if (hasQueue && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                if (!guild.getAudioManager().isConnected()) {
                    guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
                }
                mgr.scheduler.nextTrack();
            }
        }

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildMainView(mgr, session))
                        .build()
        ).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(msg -> session.message = msg));
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BTN)
    public void onButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/music` to open a new Music Box.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager mgr = getGuildAudioPlayer(guild);
        String id = event.getComponentId().substring(BTN.length());

        // Check if this button interaction is from a temp popup message
        boolean isFromTemp = session.tempMessage != null
                && event.getMessage().getIdLong() == session.tempMessage.getIdLong();

        // If the interaction comes from a temp message, handle cleanup
        if (isFromTemp) {
            switch (id) {
                case "back" -> {
                    // "Cancel" from search results temp - just delete the temp
                    event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
                    session.tempMessage = null;
                    session.tempHook = null;
                    session.view = View.MAIN;
                    return;
                }
                case "queueview" -> {
                    // "Back to Queue" from remove menu temp - delete temp, refresh main to queue
                    event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
                    session.tempMessage = null;
                    session.tempHook = null;
                    session.view = View.QUEUE;
                    session.page = 0;
                    if (session.message != null) {
                        session.message.editMessage(
                                new MessageEditBuilder()
                                        .useComponentsV2(true)
                                        .setComponents(buildQueueView(session))
                                        .build()
                        ).queue();
                    }
                    return;
                }
                default -> {
                    // Any other button from temp: just acknowledge
                    event.deferEdit().queue();
                    return;
                }
            }
        }

        switch (id) {
            // ---- Playback Controls ----
            case "pause" -> {
                mgr.player.setPaused(!mgr.player.isPaused());
                editToMainView(event, mgr, session);
            }
            case "skip" -> {
                mgr.scheduler.nextTrack();
                editToMainView(event, mgr, session);
            }
            case "loop" -> {
                mgr.scheduler.cycleLoopMode();
                editToMainView(event, mgr, session);
            }
            case "shuffle" -> {
                mgr.scheduler.toggleShuffle();
                editToMainView(event, mgr, session);
            }

            // ---- Navigation ----
            case "queuesong" -> showQueueSongModal(event);
            case "queueview" -> {
                session.view = View.QUEUE;
                session.page = 0;
                editToQueueView(event, session);
            }
            case "history" -> {
                session.view = View.HISTORY;
                session.page = 0;
                editToHistoryView(event, session);
            }
            case "back" -> {
                session.view = View.MAIN;
                session.page = 0;
                editToMainView(event, mgr, session);
            }

            // ---- Queue View Controls ----
            case "qprev" -> {
                session.page = Math.max(0, session.page - 1);
                editToQueueView(event, session);
            }
            case "qnext" -> {
                session.page++;
                editToQueueView(event, session);
            }
            case "remove" -> showRemoveMenu(event, session);
            case "reindex" -> {
                reindexQueue(session);
                editToQueueView(event, session);
            }
            case "clearqueue" -> {
                clearGuildQueue(session.guildId);
                session.page = 0;
                editToQueueView(event, session);
            }

            // ---- History View Controls ----
            case "hprev" -> {
                session.page = Math.max(0, session.page - 1);
                editToHistoryView(event, session);
            }
            case "hnext" -> {
                session.page++;
                editToHistoryView(event, session);
            }

            // ---- Voice Join ----
            case "join" -> {
                Member member = event.getMember();
                if (member != null && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                    guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
                    editToMainView(event, mgr, session);
                } else {
                    event.reply("❌ You must be in a voice channel!")
                            .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL_PREFIX)
    public void onModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());

        if ("queuesong".equals(modalId)) {
            String query = getModalValue(event, "query");

            if (query.isEmpty()) {
                event.reply("❌ Please enter a search query or URL!").setEphemeral(true)
                        .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                return;
            }

            // Read source selection (StringSelectMenu returns STRING_SELECT type)
            var sourceMapping = event.getValue("source");
            String source = "auto";
            if (sourceMapping != null && !sourceMapping.getAsStringList().isEmpty()) {
                source = sourceMapping.getAsStringList().getFirst();
            }

            // Build search prefix
            String searchPrefix;
            if (query.startsWith("http")) {
                searchPrefix = "";
            } else {
                searchPrefix = switch (source) {
                    case "youtube" -> "ytsearch:";
                    case "soundcloud" -> "scsearch:";
                    default -> "ytsearch:"; // auto
                };
            }

            Guild guild = event.getGuild();
            if (guild == null) return;

            Member member = event.getMember();
            GuildMusicManager mgr = getGuildAudioPlayer(guild);

            // Auto-join voice if not connected
            if (!guild.getAudioManager().isConnected() && member != null
                    && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
            }

            // Acknowledge the modal with a loading message (kept as temp for search results)
            session.cleanupTemp(); // Clean any previous temp message
            final String finalSearchPrefix = searchPrefix;
            event.reply(
                    new MessageCreateBuilder()
                            .useComponentsV2(true)
                            .setComponents(Container.of(
                                    TextDisplay.of("# 🔍 Searching..."),
                                    Separator.create(false, Separator.Spacing.SMALL),
                                    TextDisplay.of("_Loading results, please wait..._")
                            ).withAccentColor(ACCENT_PLAYING))
                            .build()
            ).setEphemeral(true).queue(hook -> {
                session.tempHook = hook;
                hook.retrieveOriginal().queue(msg -> session.tempMessage = msg);

                // Start loading AFTER the hook is set, so callbacks can use tempHook
                playerManager.loadItemOrdered(mgr, finalSearchPrefix + query, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        mgr.scheduler.queue(track, userId);
                        // Direct load: clean up temp message and refresh main Music Box
                        session.cleanupTemp();
                        updateSessionMessage(session, mgr);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        if (playlist.isSearchResult()) {
                            handleSearchResults(session, playlist);
                        } else {
                            for (AudioTrack track : playlist.getTracks()) {
                                mgr.scheduler.queue(track, userId);
                            }
                            // Playlist load: clean up temp message and refresh main Music Box
                            session.cleanupTemp();
                            updateSessionMessage(session, mgr);
                        }
                    }

                    @Override
                    public void noMatches() {
                        // Edit temp to show no results, auto-delete
                        if (session.tempHook != null) {
                            session.tempHook.editOriginal("❌ No matches found!").queue(
                                    _ -> session.tempHook.deleteOriginal().queueAfter(5, TimeUnit.SECONDS)
                            );
                        }
                        session.tempMessage = null;
                        session.tempHook = null;
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        // Edit temp to show error, auto-delete
                        if (session.tempHook != null) {
                            session.tempHook.editOriginal("❌ Failed to load: " + exception.getMessage()).queue(
                                    _ -> session.tempHook.deleteOriginal().queueAfter(5, TimeUnit.SECONDS)
                            );
                        }
                        session.tempMessage = null;
                        session.tempHook = null;
                    }
                });
            });
        }
    }

    // ==================== SELECT MENU HANDLER ====================

    @SelectMenuHandler(MENU_PREFIX)
    public void onSelectMenu(StringSelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);
        if (session == null) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        String menuId = event.getComponentId();

        // Search result selection
        if (menuId.startsWith(MENU_PREFIX + "select:")) {
            String searchId = menuId.substring((MENU_PREFIX + "select:").length());
            List<AudioTrack> tracks = searchCache.get(searchId);

            if (tracks == null) {
                event.reply("❌ Search expired.").setEphemeral(true)
                        .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                return;
            }

            int index = Integer.parseInt(event.getValues().getFirst());
            AudioTrack selected = tracks.get(index);

            GuildMusicManager mgr = getGuildAudioPlayer(guild);
            mgr.scheduler.queue(selected, userId);
            searchCache.remove(searchId);

            // Acknowledge and delete the temp search message
            event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
            session.tempMessage = null;
            session.tempHook = null;

            // Refresh the original Music Box
            session.view = View.MAIN;
            updateSessionMessage(session, mgr);
            return;
        }

        // Queue remove selection
        if (menuId.equals(MENU_PREFIX + "remove")) {
            String dbIdStr = event.getValues().getFirst();
            long dbId = Long.parseLong(dbIdStr);

            Optional<QueueEntry> entryOpt = queueRepo.findById(dbId);
            if (entryOpt.isPresent() && entryOpt.get().getGuildId() == session.guildId) {
                queueRepo.deleteById(dbId);
            }

            // Acknowledge and delete the temp remove message
            event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
            session.tempMessage = null;
            session.tempHook = null;

            // Refresh the original Music Box to queue view
            session.view = View.QUEUE;
            if (session.message != null) {
                session.message.editMessage(
                        new MessageEditBuilder()
                                .useComponentsV2(true)
                                .setComponents(buildQueueView(session))
                                .build()
                ).queue();
            }
        }
    }

    // ==================== VIEW BUILDERS ====================

    private Container buildMainView(GuildMusicManager mgr, MusicSession session) {
        AudioTrack current = mgr.player.getPlayingTrack();
        List<ContainerChildComponent> children = new ArrayList<>();

        if (current != null) {
            // Now Playing with thumbnail
            String titleText = "### 🎵 [" + current.getInfo().title + "](" + current.getInfo().uri + ")";

            if (current.getInfo().artworkUrl != null && !current.getInfo().artworkUrl.isEmpty()) {
                children.add(Section.of(
                        Thumbnail.fromUrl(current.getInfo().artworkUrl),
                        TextDisplay.of(titleText),
                        TextDisplay.of("-# " + current.getInfo().author)
                ));
            } else {
                children.add(TextDisplay.of(titleText));
                children.add(TextDisplay.of("-# " + current.getInfo().author));
            }

            children.add(Separator.create(false, Separator.Spacing.SMALL));

            // Progress & Status
            String loopIcon = switch (mgr.scheduler.loopMode) {
                case 1 -> "🔁 Queue";
                case 2 -> "🔂 Track";
                default -> "➡ Off";
            };
            String shuffleIcon = mgr.scheduler.shuffle ? "🔀 On" : "➡ Off";
            String pauseIcon = mgr.player.isPaused() ? "⏸ Paused" : "▶ Playing";

            children.add(TextDisplay.of(
                    "⏱ " + formatTime(current.getPosition()) + " / " + formatTime(current.getDuration())
                            + "\u2003\u2003" + pauseIcon
                            + "\n🔁 Loop: " + loopIcon + "\u2003\u2003🔀 Shuffle: " + shuffleIcon
            ));

            children.add(Separator.create(true, Separator.Spacing.SMALL));

            // Playback controls
            boolean isPaused = mgr.player.isPaused();
            children.add(ActionRow.of(
                    Button.primary(BTN + "pause", isPaused ? "▶ Resume" : "⏸ Pause"),
                    Button.secondary(BTN + "skip", "⏭ Skip"),
                    Button.secondary(BTN + "loop", "🔁 Loop"),
                    Button.secondary(BTN + "shuffle", "🔀 Shuffle")
            ));
        } else {
            children.add(TextDisplay.of("# 🎵 Music Box"));
            children.add(Separator.create(false, Separator.Spacing.SMALL));
            children.add(TextDisplay.of("_No track playing. Queue a song to get started!_"));
            children.add(Separator.create(true, Separator.Spacing.SMALL));
        }

        // Navigation
        children.add(ActionRow.of(
                Button.success(BTN + "queuesong", "🎵 Queue Song"),
                Button.primary(BTN + "queueview", "📋 Queue"),
                Button.secondary(BTN + "history", "📜 History")
        ));

        Color accent = current != null ? ACCENT_PLAYING : ACCENT_IDLE;
        return Container.of(children).withAccentColor(accent);
    }

    private Container buildQueueView(MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .list();

        int totalItems = queue.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        session.page = Math.min(session.page, totalPages - 1);

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📋 Queue"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (queue.isEmpty()) {
            children.add(TextDisplay.of("_Queue is empty. Use **🎵 Queue Song** to add tracks!_"));
        } else {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                QueueEntry entry = queue.get(i);
                String title = entry.getTitle();
                if (title.length() > 60) title = title.substring(0, 57) + "...";
                sb.append(String.format("`%d.` %s\n", i + 1, title));
            }
            children.add(TextDisplay.of(sb.toString()));
            children.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages
                    + " • " + totalItems + " tracks total"));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));

        // Pagination + manipulation
        children.add(ActionRow.of(
                Button.secondary(BTN + "qprev", "◀").withDisabled(session.page <= 0),
                Button.secondary(BTN + "qnext", "▶").withDisabled(session.page >= totalPages - 1),
                Button.danger(BTN + "remove", "🗑 Remove").withDisabled(queue.isEmpty()),
                Button.secondary(BTN + "reindex", "🔀 Shuffle Queue").withDisabled(queue.isEmpty()),
                Button.danger(BTN + "clearqueue", "🧹 Clear All").withDisabled(queue.isEmpty())
        ));
        children.add(ActionRow.of(
                Button.primary(BTN + "back", "🔙 Back to Player")
        ));

        return Container.of(children).withAccentColor(ACCENT_QUEUE);
    }

    private Container buildHistoryView(MusicSession session) {
        List<HistoryEntry> history = historyRepo.query()
                .where("guild_id", session.guildId)
                .orderByDesc("played_at")
                .list();

        int totalItems = history.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        session.page = Math.min(session.page, totalPages - 1);

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📜 History"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (history.isEmpty()) {
            children.add(TextDisplay.of("_No history yet. Play some music!_"));
        } else {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                HistoryEntry h = history.get(i);
                String time = h.getPlayedAt().toString();
                String title = h.getTrackTitle();
                if (title.length() > 50) title = title.substring(0, 47) + "...";
                sb.append(String.format("<t:%s:S> [%s](%s)\n", time, title, h.getTrackUrl()));
            }
            children.add(TextDisplay.of(sb.toString()));
            children.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages
                    + " • " + totalItems + " entries"));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));

        children.add(ActionRow.of(
                Button.secondary(BTN + "hprev", "◀").withDisabled(session.page <= 0),
                Button.secondary(BTN + "hnext", "▶").withDisabled(session.page >= totalPages - 1),
                Button.primary(BTN + "back", "🔙 Back to Player")
        ));

        return Container.of(children).withAccentColor(ACCENT_HISTORY);
    }

    private Container buildSearchView(MusicSession session, List<AudioTrack> tracks, String searchId) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🔍 Search Results"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            String title = t.getInfo().title;
            if (title.length() > 60) title = title.substring(0, 57) + "...";
            sb.append(String.format("`%d.` **%s** — %s (%s)\n",
                    i + 1, title, t.getInfo().author, formatTime(t.getDuration())));
        }
        children.add(TextDisplay.of(sb.toString()));

        // Select menu
        StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_PREFIX + "select:" + searchId)
                .setPlaceholder("Select a track to queue...");

        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            String label = (i + 1) + ". " + t.getInfo().title;
            if (label.length() > 100) label = label.substring(0, 97) + "...";
            menu.addOption(label, String.valueOf(i), t.getInfo().author);
        }

        children.add(ActionRow.of(menu.build()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.primary(BTN + "back", "🔙 Cancel")
        ));

        return Container.of(children).withAccentColor(ACCENT_PLAYING);
    }

    private Container buildRemoveView(MusicSession session, List<QueueEntry> queue) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🗑 Remove from Queue"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(TextDisplay.of("Select a track to remove:"));

        StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_PREFIX + "remove")
                .setPlaceholder("Select a track to remove...");

        for (int i = 0; i < Math.min(25, queue.size()); i++) {
            QueueEntry entry = queue.get(i);
            String label = (i + 1) + ". " + entry.getTitle();
            if (label.length() > 100) label = label.substring(0, 97) + "...";
            menu.addOption(label, String.valueOf(entry.getId()));
        }

        children.add(ActionRow.of(menu.build()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.primary(BTN + "queueview", "🔙 Back to Queue")
        ));

        return Container.of(children).withAccentColor(ACCENT_QUEUE);
    }

    // ==================== VIEW EDIT HELPERS ====================

    private void editToMainView(ButtonInteractionEvent event, GuildMusicManager mgr, MusicSession session) {
        session.view = View.MAIN;
        event.editMessage(
                new MessageEditBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildMainView(mgr, session))
                        .build()
        ).queue();
    }

    private void editToQueueView(ButtonInteractionEvent event, MusicSession session) {
        event.editMessage(
                new MessageEditBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildQueueView(session))
                        .build()
        ).queue();
    }

    private void editToHistoryView(ButtonInteractionEvent event, MusicSession session) {
        event.editMessage(
                new MessageEditBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildHistoryView(session))
                        .build()
        ).queue();
    }

    /** Update the stored message reference asynchronously (after audio load completes) */
    private void updateSessionMessage(MusicSession session, GuildMusicManager mgr) {
        if (session.message != null) {
            session.view = View.MAIN;
            session.message.editMessage(
                    new MessageEditBuilder()
                            .useComponentsV2(true)
                            .setComponents(buildMainView(mgr, session))
                            .build()
            ).queue();
        }
    }

    // ==================== MODAL BUILDERS ====================

    private void showQueueSongModal(ButtonInteractionEvent event) {StringSelectMenu sourceMenu = StringSelectMenu.create("source")
                .setPlaceholder("Search source")
                .addOption("🔍 Auto-detect (URL or YouTube)", "auto")
                .addOption("▶ YouTube", "youtube")
                .addOption("☁ SoundCloud", "soundcloud")
                .setDefaultOptions(SelectOption.of("🔍 Auto-detect (URL or YouTube)", "auto"))
                .build();

        event.replyModal(Modal.create(MODAL_PREFIX + "queuesong", "Queue a Song")
                .addComponents(
                        Label.of("URL or Search Query", TextInput.create("query", TextInputStyle.SHORT)
                                .setPlaceholder("Paste a URL or type a search query...")
                                .setMaxLength(500)
                                .setRequired(true)
                                .build()
                        ),
                        Label.of("Search Source", sourceMenu)
                ).build()
        ).queue();
    }

    private void showRemoveMenu(ButtonInteractionEvent event, MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .limit(25)
                .list();

        if (queue.isEmpty()) {
            event.reply("ℹ️ Queue is empty!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Clean up any previous temp message
        session.cleanupTemp();

        // Send as a NEW ephemeral message (don't touch the original Music Box)
        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildRemoveView(session, queue))
                        .build()
        ).setEphemeral(true).queue(hook -> {
            session.tempHook = hook;
            hook.retrieveOriginal().queue(msg -> session.tempMessage = msg);
        });
    }

    // ==================== SEARCH RESULT HANDLING ====================

    private void handleSearchResults(MusicSession session, AudioPlaylist playlist) {
        List<AudioTrack> tracks = playlist.getTracks().subList(0, Math.min(5, playlist.getTracks().size()));
        String searchId = UUID.randomUUID().toString();
        searchCache.put(searchId, tracks);

        // Edit the temp loading message into search results (NOT the original Music Box)
        if (session.tempHook != null) {
            session.view = View.SEARCH;
            session.tempHook.editOriginal(
                    new MessageEditBuilder()
                            .useComponentsV2(true)
                            .setComponents(buildSearchView(session, tracks, searchId))
                            .build()
            ).queue(msg -> session.tempMessage = msg);
        }
    }

    // ==================== QUEUE MANIPULATION ====================

    private void reindexQueue(MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .list();

        if (queue.size() <= 1) return;

        // Shuffle queue order: delete all then re-insert in random order
        Collections.shuffle(queue);
        for (QueueEntry entry : queue) {
            queueRepo.deleteById(entry.getId());
        }
        for (QueueEntry entry : queue) {
            entry.setId(null); // Reset ID for new auto-increment ordering
            queueRepo.save(entry);
        }
    }

    /** Clears all QUEUE-status entries for a guild (does not touch CURRENT or PLAYED) */
    private void clearGuildQueue(long guildId) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", guildId)
                .where("status", "QUEUE")
                .list();

        for (QueueEntry entry : queue) {
            queueRepo.deleteById(entry.getId());
        }
    }

    /**
     * Recovers stale queue entries after plugin reload / force restart.
     * Moves CURRENT and ERROR entries back to QUEUE so they can be replayed.
     * Also cleans up PLAYED entries that are no longer useful.
     */
    private void recoverStaleQueue(long guildId) {
        // Move stale CURRENT entries back to QUEUE (interrupted playback)
        List<QueueEntry> stale = queueRepo.query()
                .where("guild_id", guildId)
                .where("status", "CURRENT")
                .list();

        for (QueueEntry entry : stale) {
            entry.setStatus("QUEUE");
            entry.setIsLooped(false);
            queueRepo.save(entry);
        }

        // Move ERROR entries back to QUEUE (may succeed on retry)
        List<QueueEntry> errors = queueRepo.query()
                .where("guild_id", guildId)
                .where("status", "ERROR")
                .list();

        for (QueueEntry entry : errors) {
            entry.setStatus("QUEUE");
            entry.setIsLooped(false);
            queueRepo.save(entry);
        }

        // Clean up PLAYED entries (stale from previous session, not needed for queue)
        List<QueueEntry> played = queueRepo.query()
                .where("guild_id", guildId)
                .where("status", "PLAYED")
                .list();

        for (QueueEntry entry : played) {
            queueRepo.deleteById(entry.getId());
        }
    }

    // ==================== VOICE EVENT ====================

    @EventHandler
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelLeft() != null) {
            Guild guild = event.getGuild();
            if (event.getMember().getUser().getIdLong() == guild.getSelfMember().getUser().getIdLong()) {
                GuildMusicManager mgr = musicManagers.get(guild.getIdLong());
                if (mgr != null) {
                    mgr.player.destroy();
                    mgr.scheduler.clearQueue();
                    musicManagers.remove(guild.getIdLong());
                }
            }
        }
    }

    // ==================== AUDIO MANAGER ====================

    private GuildMusicManager getGuildAudioPlayer(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), _ -> {
            GuildMusicManager mgr = new GuildMusicManager(playerManager, guild.getIdLong());
            guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(mgr.player));
            return mgr;
        });
    }

    // ==================== UTILITIES ====================

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }

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
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ==================== INNER CLASSES ====================

    private enum View { MAIN, QUEUE, HISTORY, SEARCH }

    private static class MusicSession {
        final long userId;
        final long guildId;
        Message message;       // The main Music Box message (persistent)
        Message tempMessage;   // Temporary popup message for search results / remove menu
        InteractionHook tempHook; // Hook for temp message lifecycle
        View view = View.MAIN;
        int page = 0;

        MusicSession(long userId, long guildId) {
            this.userId = userId;
            this.guildId = guildId;
        }

        /** Clean up the temporary popup message if it exists */
        void cleanupTemp() {
            if (tempMessage != null) {
                tempMessage.delete().queue(null, _ -> {});
                tempMessage = null;
            }
            tempHook = null;
        }
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

        public int loopMode = 0; // 0=Off, 1=Queue, 2=Track
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
            // 1. Move CURRENT -> PLAYED
            List<QueueEntry> active = queueRepo.query()
                    .where("guild_id", guildId)
                    .where("status", "CURRENT")
                    .list();

            for (QueueEntry e : active) {
                e.setStatus("PLAYED");
                queueRepo.save(e);

                if (e.getIsLooped() == null || !e.getIsLooped()) {
                    try {
                        AudioTrack infoTrack = decodeTrack(e.getTrackBlob());
                        HistoryEntry hist = new HistoryEntry();
                        hist.setGuildId(guildId);
                        hist.setUserId(e.getUserId());
                        hist.setTrackTitle(e.getTitle());
                        hist.setTrackUrl(infoTrack.getInfo().uri);
                        hist.setPlayedAt(Instant.now().getEpochSecond());
                        historyRepo.save(hist);
                    } catch (IOException ex) {
                        context.log("error", "History save failed: " + ex.getMessage());
                    }
                }
            }

            // 2. Fetch next
            QueueEntry nextEntry = null;
            QueryBuilder<QueueEntry> query = queueRepo.query()
                    .where("guild_id", guildId)
                    .where("status", "QUEUE");

            if (shuffle) {
                List<QueueEntry> candidates = query.list();
                if (!candidates.isEmpty()) {
                    nextEntry = candidates.get(new Random().nextInt(candidates.size()));
                }
            } else {
                List<QueueEntry> list = query.limit(1).list();
                if (!list.isEmpty()) nextEntry = list.getFirst();
            }

            // 3. Loop Queue: recycle PLAYED -> QUEUE
            if (nextEntry == null && loopMode == 1) {
                List<QueueEntry> played = queueRepo.query()
                        .where("guild_id", guildId)
                        .where("status", "PLAYED")
                        .list();

                if (!played.isEmpty()) {
                    for (QueueEntry e : played) {
                        e.setStatus("QUEUE");
                        e.setIsLooped(true);
                        queueRepo.save(e);
                    }
                    nextTrack();
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

        public void cycleLoopMode() { loopMode = (loopMode + 1) % 3; }
        public void toggleShuffle() { shuffle = !shuffle; }
        public void clearQueue() { queueRepo.deleteBy("guild_id", guildId); }
    }

    // ==================== ENTITIES ====================

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