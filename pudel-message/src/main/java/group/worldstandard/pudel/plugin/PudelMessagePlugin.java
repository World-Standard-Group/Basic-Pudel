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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive Embed Builder Plugin for Pudel Discord Bot
 * <p>
 * Features:
 * - Single slash command entry point
 * - Live visual preview
 * - Button-based editing and posting
 * - Channel selection via UI
 *
 * @author Zazalng
 * @version 2.2.0
 */
@Plugin(
        name = "Pudel's Embed Builder",
        version = "2.2.0",
        author = "Zazalng",
        description = "Interactive embed builder with live preview"
)
public class PudelMessagePlugin {

    // ==================== CONSTANTS ====================
    private static final String BUTTON_PREFIX = "embed:";
    private static final String MODAL_PREFIX = "embed:modal:";
    private static final String MENU_PREFIX = "embed:menu:";
    private static final String CHANNEL_SELECT_ID = "embed:channel_select";

    // ==================== STATE MANAGEMENT ====================
    private PluginContext context;
    private final Map<Long, EmbedSession> activeSessions = new ConcurrentHashMap<>();

    // ==================== LIFECYCLE HOOKS ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        ctx.log("info", "PudelMessagePlugin initialized (v2.2.0)");
    }

    @OnShutdown
    public boolean onShutdown() {
        for (EmbedSession session : activeSessions.values()) {
            if (session.previewMessage != null) {
                session.previewMessage.delete().queue(null, e -> {});
            }
        }
        activeSessions.clear();
        return true;
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "embed",
            description = "Open the interactive embed builder"
    )
    public void handleEmbedCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();

        // Delete old session if exists
        EmbedSession oldSession = activeSessions.get(userId);
        if (oldSession != null && oldSession.previewMessage != null) {
            oldSession.previewMessage.delete().queue(null, e -> {});
        }

        // Create new session
        EmbedSession session = new EmbedSession(userId);
        activeSessions.put(userId, session);

        // Send builder interface
        event.replyEmbeds(buildPreviewEmbed(session))
                .setContent("🛠️ **Embed Builder**\nUse the buttons below to edit. The embed shown is your **live preview**.")
                .setEphemeral(true)
                .addComponents(getBuilderActionRows())
                .queue(hook -> hook.retrieveOriginal().queue(msg -> session.previewMessage = msg));
    }

    // ==================== COMPONENT HANDLERS ====================

    @ButtonHandler(BUTTON_PREFIX)
    public void handleButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/embed` to start again.").setEphemeral(true).queue();
            return;
        }

        String buttonId = event.getComponentId().substring(BUTTON_PREFIX.length());

        switch (buttonId) {
            // Content
            case "title" -> showTitleModal(event);
            case "description" -> showDescriptionModal(event);
            case "color" -> showColorSelectMenu(event);
            case "author" -> showAuthorModal(event);
            case "footer" -> showFooterModal(event);
            case "thumbnail" -> showThumbnailModal(event);
            case "image" -> showImageModal(event);
            case "url" -> showUrlModal(event);
            case "timestamp" -> showTimestampModal(event);

            // Fields
            case "field" -> showFieldModal(event);
            case "clearfields" -> {
                session.fields.clear();
                updateSessionPreview(event, session);
            }

            // Actions
            case "post" -> showChannelSelect(event);
            case "cancel" -> {
                if (session.previewMessage != null) session.previewMessage.delete().queue();
                activeSessions.remove(userId);
                event.reply("❌ Session cancelled.").setEphemeral(true).queue();
            }
        }
    }

    // This handles the Channel Selection for "Post"
    @SelectMenuHandler(CHANNEL_SELECT_ID)
    public void handleChannelSelect(EntitySelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired.").setEphemeral(true).queue();
            return;
        }

        List<GuildChannelUnion> channels = event.getMentions().getChannels(GuildChannelUnion.class);
        if (channels.isEmpty()) return;

        GuildMessageChannel targetChannel = channels.getFirst().asGuildMessageChannel();

        // Permission Check
        if (!targetChannel.canTalk()) {
            event.reply("❌ I cannot send messages to " + targetChannel.getAsMention()).setEphemeral(true).queue();
            return;
        }

        // Build and Post
        MessageEmbed finalEmbed = buildFinalEmbed(session);
        targetChannel.sendMessageEmbeds(finalEmbed).queue(
                success -> {
                    // Cleanup
                    if (session.previewMessage != null) session.previewMessage.delete().queue();
                    activeSessions.remove(userId);
                    event.reply("✅ Embed posted in " + targetChannel.getAsMention()).setEphemeral(true).queue();
                },
                error -> event.reply("❌ Failed to post: " + error.getMessage()).setEphemeral(true).queue()
        );
    }

    @ModalHandler(MODAL_PREFIX)
    public void handleModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true).queue();
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
                    session.authorUrl = (isValidUrl(url)) ? url : null;
                    session.authorIcon = (isValidUrl(icon)) ? icon : null;
                }
                case "footer" -> {
                    String foot = getModalValue(event, "footer");
                    String icon = getModalValue(event, "footericon");
                    session.footer = foot.isEmpty() ? null : foot;
                    session.footerIcon = (isValidUrl(icon)) ? icon : null;
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
                            event.reply("❌ Invalid format! Use: DD-MM-YYYY HH:mm:ss+OFFSET").setEphemeral(true).queue();
                            return;
                        }
                    } else session.timestamp = null;
                }
                case "field" -> {
                    if (session.fields.size() >= 25) {
                        event.reply("❌ Max 25 fields!").setEphemeral(true).queue();
                        return;
                    }
                    String name = getModalValue(event, "fieldname");
                    String value = getModalValue(event, "fieldvalue");
                    String inlineStr = getModalValue(event, "fieldinline").toLowerCase();
                    boolean inline = inlineStr.startsWith("y") || inlineStr.equals("true");
                    session.fields.add(new EmbedField(name, value, inline));
                }
                case "customcolor" -> {
                    Color c = parseColor(getModalValue(event, "colorhex"));
                    if (c != null) session.color = c;
                    else {
                        event.reply("❌ Invalid hex!").setEphemeral(true).queue();
                        return;
                    }
                }
            }
            updateSessionPreviewFromModal(event, session);
        } catch (Exception e) {
            event.reply("❌ Error: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

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

        // Predefined colors
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
        updateSessionPreview(event, session);
    }

    // ==================== HELPER METHODS ====================

    private void showChannelSelect(ButtonInteractionEvent event) {
        EntitySelectMenu channelMenu = EntitySelectMenu.create(CHANNEL_SELECT_ID, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("Select a channel to post this embed")
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setMaxValues(1)
                .build();

        event.reply("Select the channel to post this embed:")
                .setEphemeral(true)
                .addComponents(ActionRow.of(channelMenu))
                .queue();
    }

    private String validateUrl(ModalInteractionEvent event, String fieldId) {
        String url = getModalValue(event, fieldId);
        if (url.isEmpty()) return null;
        if (isValidUrl(url)) return url;
        // Note: In a real scenario, we might want to return null and warn user,
        // but for simplicity in modal flow we return null (clearing it)
        return null;
    }

    private void updateSessionPreview(ButtonInteractionEvent event, EmbedSession session) {
        event.editMessageEmbeds(buildPreviewEmbed(session))
                .setComponents(getBuilderActionRows())
                .queue();
    }

    // Overload for StringSelectInteraction
    private void updateSessionPreview(StringSelectInteractionEvent event, EmbedSession session) {
        if(session.previewMessage != null) {
            session.previewMessage.editMessageEmbeds(buildPreviewEmbed(session))
                    .setComponents(getBuilderActionRows())
                    .queue();
        }
    }

    private void updateSessionPreviewFromModal(ModalInteractionEvent event, EmbedSession session) {
        if (session.previewMessage != null) {
            session.previewMessage.editMessageEmbeds(buildPreviewEmbed(session))
                    .setComponents(getBuilderActionRows())
                    .queue();
        }
        event.reply("✅ Updated!").setEphemeral(true).queue();
    }

    private List<ActionRow> getBuilderActionRows() {
        return List.of(
                ActionRow.of(
                        Button.primary(BUTTON_PREFIX + "title", "📝 Title"),
                        Button.primary(BUTTON_PREFIX + "description", "📄 Desc"),
                        Button.primary(BUTTON_PREFIX + "color", "🎨 Color"),
                        Button.primary(BUTTON_PREFIX + "author", "👤 Author"),
                        Button.primary(BUTTON_PREFIX + "footer", "📌 Footer")
                ),
                ActionRow.of(
                        Button.secondary(BUTTON_PREFIX + "thumbnail", "🖼️ Thumb"),
                        Button.secondary(BUTTON_PREFIX + "image", "🌄 Image"),
                        Button.secondary(BUTTON_PREFIX + "url", "🔗 URL"),
                        Button.secondary(BUTTON_PREFIX + "timestamp", "⏰ Time")
                ),
                ActionRow.of(
                        Button.success(BUTTON_PREFIX + "field", "➕ Field"),
                        Button.danger(BUTTON_PREFIX + "clearfields", "🗑️ Clear Fields")
                ),
                ActionRow.of(
                        Button.success(BUTTON_PREFIX + "post", "✅ Post Embed"),
                        Button.danger(BUTTON_PREFIX + "cancel", "✖ Cancel")
                )
        );
    }

    // ... Modal Builders (mostly same as before, simplified for brevity) ...

    private void showTitleModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "title", "Title")
                .addComponents(
                        Label.of("Embed Title", TextInput.create("title", TextInputStyle.SHORT)
                                .setPlaceholder("Title")
                                .setMaxLength(256)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showDescriptionModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "description", "Description")
                .addComponents(
                        Label.of("Embed Description", TextInput.create("description", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Description Text")
                                .setMaxLength(4000)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showAuthorModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "author", "Author")
                .addComponents(
                        Label.of("Embed Author Name", TextInput.create("author", TextInputStyle.SHORT)
                                .setPlaceholder("Author Name")
                                .setMaxLength(255)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Author URL", TextInput.create("authorurl", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Icon URL", TextInput.create("authoricon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showFooterModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "footer", "Footer")
                .addComponents(
                        Label.of("Embed Foot Note", TextInput.create("footer", TextInputStyle.SHORT)
                                .setPlaceholder("Foot note")
                                .setMaxLength(2048)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Foot Icon", TextInput.create("footericon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showThumbnailModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "thumbnail", "Thumbnail")
                .addComponents(
                        Label.of("Embed Thumbnail URL", TextInput.create("thumbnail", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showImageModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "image", "Image")
                .addComponents(
                        Label.of("Embed Image URL", TextInput.create("image", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showUrlModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "url", "Title URL")
                .addComponents(
                        Label.of("Embed Title URL", TextInput.create("url", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showTimestampModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "timestamp", "Timestamp")
                .addComponents(
                        Label.of("Embed Timestamp", TextInput.create("timestamp", TextInputStyle.SHORT)
                                .setPlaceholder(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ssX")))
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showFieldModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "field", "Add Field")
                .addComponents(
                        Label.of("Field Title", TextInput.create("fieldname", TextInputStyle.SHORT)
                                .setPlaceholder("Header")
                                .setMaxLength(256)
                                .build()
                        ),
                        Label.of("Field Content", TextInput.create("fieldvalue", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Value")
                                .setMaxLength(1024)
                                .build()
                        ),
                        Label.of("Field Inline?", TextInput.create("fieldinline", TextInputStyle.SHORT)
                                .setPlaceholder("yes / no")
                                .setRequired(true)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showColorSelectMenu(ButtonInteractionEvent event) {
        StringSelectMenu menu = StringSelectMenu.create(MENU_PREFIX + "color")
                .addOption("🔴 Red", "red").addOption("🔵 Blue", "blue").addOption("🟢 Green", "green")
                .addOption("🟡 Yellow", "yellow").addOption("🟠 Orange", "orange").addOption("🟣 Purple", "purple")
                .addOption("⚪ White", "white").addOption("⚫ Black", "black").addOption("🎨 Custom", "custom")
                .addOption("❌ Reset", "none")
                .build();
        event.reply("Select Color")
                .setEphemeral(true)
                .addComponents(ActionRow.of(menu))
                .queue();
    }

    private MessageEmbed buildPreviewEmbed(EmbedSession session) {
        boolean hasContent = session.title != null || session.description != null || session.author != null
                || session.footer != null || session.image != null || session.thumbnail != null
                || session.timestamp != null || !session.fields.isEmpty();

        if (!hasContent) {
            EmbedBuilder b = new EmbedBuilder();
            b.setTitle("🆕 New Embed");
            b.setDescription("_Start adding content using the buttons below._\n_This preview will update automatically._");
            b.setColor(Color.LIGHT_GRAY);
            return b.build();
        }
        return buildFinalEmbed(session);
    }

    private MessageEmbed buildFinalEmbed(EmbedSession session) {
        EmbedBuilder b = new EmbedBuilder();
        if (session.title != null) b.setTitle(session.title, session.url);
        if (session.description != null) b.setDescription(session.description);
        if (session.color != null) b.setColor(session.color);
        if (session.author != null) b.setAuthor(session.author, session.authorUrl, session.authorIcon);
        if (session.footer != null) b.setFooter(session.footer, session.footerIcon);
        if (session.thumbnail != null) b.setThumbnail(session.thumbnail);
        if (session.image != null) b.setImage(session.image);
        if (session.timestamp != null) b.setTimestamp(session.timestamp);
        for (EmbedField f : session.fields) b.addField(f.name, f.value, f.inline);

        if (b.isEmpty()) b.setDescription("_Empty Embed_");
        return b.build();
    }

    private boolean isValidUrl(String url) {
        try { new URI(url); return url.startsWith("http"); } catch (Exception e) { return false; }
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id); return v != null ? v.getAsString() : "";
    }

    private Color parseColor(String hex) {
        try { return Color.decode("#" + hex.replace("#", "")); } catch (Exception e) { return null; }
    }

    private OffsetDateTime parseTimestamp(String ts) {
        // Reuse the regex logic from previous version for parsing DD-MM-YYYY HH:mm:ss+Offset
        try {
            String regex = "^(?:(\\d+)[-!@#$%^&*/.]+(\\d+)[-!@#$%^&*/.]+(\\d+)\\s+)?(\\d{2}):(\\d{2}):(\\d{2})([+-]\\d+)$";
            Matcher m = Pattern.compile(regex).matcher(ts.trim());
            if (!m.matches()) return null;

            int y, mo, d;
            if(m.group(1) == null) { LocalDate now = LocalDate.now(); y=now.getYear(); mo=now.getMonthValue(); d=now.getDayOfMonth(); }
            else { int p1=Integer.parseInt(m.group(1)); int p2=Integer.parseInt(m.group(2)); int p3=Integer.parseInt(m.group(3));
                if(p1>31) { y=p1; mo=p2; d=p3; } else { d=p1; mo=p2; y=p3; } }

            return OffsetDateTime.of(y, mo, d, Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)), 0, ZoneOffset.ofHours(Integer.parseInt(m.group(7))));
        } catch(Exception e) { return null; }
    }

    private static class EmbedSession {
        final long userId;
        Message previewMessage;
        String title, description, thumbnail, image, author, authorUrl, authorIcon, footer, footerIcon, url;
        Color color;
        OffsetDateTime timestamp;
        List<EmbedField> fields = new ArrayList<>();
        EmbedSession(long u) { this.userId = u; }
    }

    private record EmbedField(String name, String value, boolean inline) {}
}