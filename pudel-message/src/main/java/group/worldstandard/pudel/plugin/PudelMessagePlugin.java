/*
 * Basic Pudel - Message Plugin Commands
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

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.plugin.helper.EmbedModalBuilder;
import group.worldstandard.pudel.plugin.session.EmbedField;
import group.worldstandard.pudel.plugin.session.EmbedSession;
import group.worldstandard.pudel.plugin.view.EmbedViewBuilder;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.net.URI;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive Embed Builder Plugin for Pudel Discord Bot.
 *
 * <p>Delegates view building to {@link EmbedViewBuilder},
 * modal creation to {@link EmbedModalBuilder}, and session
 * state to {@link EmbedSession}.
 *
 * @author Zazalng
 * @since 3.0.0
 */
@Plugin(
        name = "Pudel's Embed Builder",
        version = "3.0.1",
        author = "Zazalng",
        description = "Interactive embed builder with Components v2 live preview"
)
public class PudelMessagePlugin {

    // ==================== CONSTANTS ====================
    private static final String BUTTON_PREFIX = "embed:";
    private static final String MODAL_PREFIX = "embed:modal:";
    private static final String MENU_PREFIX = "embed:menu:";

    // ==================== STATE ====================
    private PluginContext context;
    private final Map<Long, EmbedSession> activeSessions = new ConcurrentHashMap<>();

    private EmbedViewBuilder viewBuilder;
    private EmbedModalBuilder modalBuilder;

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        this.viewBuilder = new EmbedViewBuilder(BUTTON_PREFIX);
        this.modalBuilder = new EmbedModalBuilder(MODAL_PREFIX, MENU_PREFIX);
        ctx.log("info", "%s initialized (v%s — Components v2)".formatted(ctx.getInfo().getName(), ctx.getInfo().getVersion()));
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        for (EmbedSession session : activeSessions.values()) {
            if (session.previewMessage != null) {
                session.previewMessage.delete().queue(null, _ -> {});
            }
        }
        activeSessions.clear();
        return true;
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "embed",
            description = "Open the interactive embed builder",
            nsfw = false,
            global = false,
            integrationTo = {IntegrationType.GUILD_INSTALL},
            integrationContext = {InteractionContextType.GUILD}
    )
    public void handleEmbedCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        long userId = event.getUser().getIdLong();

        EmbedSession oldSession = activeSessions.get(userId);
        if (oldSession != null && oldSession.previewMessage != null) {
            try {
                oldSession.previewMessage.delete().queue(null, _ -> {});
            } catch (IllegalStateException _) {
                context.log("warn", "message_id '".concat(oldSession.previewMessage.getId()).concat("' may long gone. Skip delete."));
            }
        }

        EmbedSession session = new EmbedSession(userId);
        activeSessions.put(userId, session);

        event.reply(viewBuilder.buildV2Message(session).build())
                .setEphemeral(true)
                .queue(hook -> hook.retrieveOriginal().queue(msg -> session.previewMessage = msg));
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BUTTON_PREFIX)
    public void handleButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/embed` to start again.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String buttonId = event.getComponentId().substring(BUTTON_PREFIX.length());

        switch (buttonId) {
            // Content modals
            case "title" -> modalBuilder.showTitleModal(event);
            case "description" -> modalBuilder.showDescriptionModal(event);
            case "color" -> modalBuilder.showColorSelectMenu(event);
            case "author" -> modalBuilder.showAuthorModal(event);
            case "footer" -> modalBuilder.showFooterModal(event);
            case "thumbnail" -> modalBuilder.showThumbnailModal(event);
            case "image" -> modalBuilder.showImageModal(event);
            case "url" -> modalBuilder.showUrlModal(event);
            case "timestamp" -> modalBuilder.showTimestampModal(event);

            // Fields
            case "field" -> modalBuilder.showFieldModal(event);
            case "clearfields" -> {
                session.fields.clear();
                event.editMessage(viewBuilder.editV2Message(session).build()).queue();
            }

            // Actions
            case "post" -> modalBuilder.showChannelSelect(event);
            case "cancel" -> {
                if (session.previewMessage != null) session.previewMessage.delete().queue();
                activeSessions.remove(userId);
                event.reply("❌ Session cancelled.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL_PREFIX)
    public void handleModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());

        try {
            switch (modalId) {
                case "title" -> {
                    String val = getModalValue(event, "title");
                    session.title = val.isEmpty() ? null : val;
                }
                case "description" -> {
                    String val = getModalValue(event, "description");
                    session.description = val.isEmpty() ? null : val;
                }
                case "author" -> {
                    String auth = getModalValue(event, "author");
                    String url = getModalValue(event, "authorurl");
                    String icon = getModalValue(event, "authoricon");
                    session.author = auth.isEmpty() ? null : auth;
                    session.authorUrl = isValidUrl(url) ? url : null;
                    session.authorIcon = isValidUrl(icon) ? icon : null;
                }
                case "footer" -> {
                    String foot = getModalValue(event, "footer");
                    String icon = getModalValue(event, "footericon");
                    session.footer = foot.isEmpty() ? null : foot;
                    session.footerIcon = isValidUrl(icon) ? icon : null;
                }
                case "thumbnail" -> session.thumbnail = validateUrl(event, "thumbnail");
                case "image" -> session.image = validateUrl(event, "image");
                case "url" -> session.url = validateUrl(event, "url");
                case "timestamp" -> {
                    String ts = getModalValue(event, "timestamp");
                    if (!ts.isEmpty()) {
                        OffsetDateTime odt = parseTimestamp(ts);
                        if (odt != null) session.timestamp = odt;
                        else {
                            event.reply("❌ Invalid format! Use: DD-MM-YYYY HH:mm:ss+OFFSET").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                            return;
                        }
                    } else session.timestamp = null;
                }
                case "field" -> {
                    if (session.fields.size() >= 25) {
                        event.reply("❌ Max 25 fields!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    String name = getModalValue(event, "fieldname");
                    String value = getModalValue(event, "fieldvalue");
                    var inlineMapping = event.getValue("fieldinline");
                    boolean inline = inlineMapping != null
                            && !inlineMapping.getAsStringList().isEmpty()
                            && inlineMapping.getAsStringList().getFirst().equals("yes");
                    session.fields.add(new EmbedField(name, value, inline));
                }
                case "customcolor" -> {
                    Color c = parseColor(getModalValue(event, "colorhex"));
                    if (c != null) session.color = c;
                    else {
                        event.reply("❌ Invalid hex!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                }
                case "post" -> {
                    var channelMapping = event.getValue("postchannel");
                    if (channelMapping == null || channelMapping.getAsStringList().isEmpty()) {
                        event.reply("❌ Please select a channel!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    String channelId = channelMapping.getAsStringList().getFirst();
                    GuildMessageChannel targetChannel = event.getGuild().getChannelById(GuildMessageChannel.class, channelId);

                    if (targetChannel == null) {
                        event.reply("❌ Channel not found or invalid.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    if (!targetChannel.canTalk()) {
                        event.reply("❌ I cannot send messages to " + targetChannel.getAsMention()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }

                    MessageEmbed finalEmbed = viewBuilder.buildFinalEmbed(session);
                    targetChannel.sendMessageEmbeds(finalEmbed).queue(
                            _ -> {
                                if (session.previewMessage != null) session.previewMessage.delete().queue();
                                activeSessions.remove(userId);
                                event.reply("✅ Embed posted in " + targetChannel.getAsMention()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                            },
                            error -> event.reply("❌ Failed to post: " + error.getMessage()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS))
                    );
                    return;
                }
            }
            // Update preview after any field change
            if (session.previewMessage != null) {
                session.previewMessage.editMessage(viewBuilder.editV2Message(session).build()).queue();
            }
            event.reply("✅ Updated!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            event.reply("❌ Error: " + e.getMessage()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    // ==================== SELECT MENU HANDLER ====================

    @SelectMenuHandler(MENU_PREFIX)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);
        if (session == null) return;

        String selected = event.getValues().getFirst();
        if (selected.equals("custom")) {
            TextInput colorInput = TextInput.create("colorhex", TextInputStyle.SHORT)
                    .setPlaceholder("Hex Color (e.g., FF0000)")
                    .setMinLength(6).setMaxLength(6).setRequired(true).build();
            event.replyModal(Modal.create(MODAL_PREFIX + "customcolor", "Custom Color")
                    .addComponents(Label.of("Color Input", colorInput)).build()).queue();
            return;
        }

        switch (selected) {
            case "red" -> session.color = Color.RED;
            case "orange" -> session.color = Color.ORANGE;
            case "yellow" -> session.color = Color.YELLOW;
            case "green" -> session.color = Color.GREEN;
            case "blue" -> session.color = Color.BLUE;
            case "purple" -> session.color = new Color(128, 0, 128);
            case "white" -> session.color = Color.WHITE;
            case "black" -> session.color = Color.BLACK;
            case "none" -> session.color = null;
        }

        event.deferEdit().queue();
        if (session.previewMessage != null) {
            session.previewMessage.editMessage(viewBuilder.editV2Message(session).build()).queue();
        }
    }

    // ==================== UTILITY METHODS ====================

    private boolean isValidUrl(String url) {
        try { new URI(url); return url.startsWith("http"); } catch (Exception e) { return false; }
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id); return v != null ? v.getAsString() : "";
    }

    private Color parseColor(String hex) {
        try { return Color.decode("#" + hex.replace("#", "")); } catch (Exception e) { return null; }
    }

    private String validateUrl(ModalInteractionEvent event, String fieldId) {
        String url = getModalValue(event, fieldId);
        if (url.isEmpty()) return null;
        if (isValidUrl(url)) return url;
        return null;
    }

    private OffsetDateTime parseTimestamp(String ts) {
        try {
            String regex = "^(?:(\\d+)[-!@#$%^&*/.]+(\\d+)[-!@#$%^&*/.]+(\\d+)\\s+)?(\\d{2}):(\\d{2}):(\\d{2})([+-]\\d+)$";
            Matcher m = Pattern.compile(regex).matcher(ts.trim());
            if (!m.matches()) return null;

            int y, mo, d;
            if (m.group(1) == null) { LocalDate now = LocalDate.now(); y = now.getYear(); mo = now.getMonthValue(); d = now.getDayOfMonth(); }
            else { int p1 = Integer.parseInt(m.group(1)); int p2 = Integer.parseInt(m.group(2)); int p3 = Integer.parseInt(m.group(3));
                if (p1 > 31) { y = p1; mo = p2; d = p3; } else { d = p1; mo = p2; y = p3; } }

            return OffsetDateTime.of(y, mo, d, Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)), 0, ZoneOffset.ofHours(Integer.parseInt(m.group(7))));
        } catch (Exception e) { return null; }
    }
}