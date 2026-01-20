package worldstandard.group.pudel.plugin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import worldstandard.group.pudel.api.SimplePlugin;
import worldstandard.group.pudel.api.command.CommandContext;

import java.awt.Color;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PudelMessagePlugin extends SimplePlugin {

    // Store active embed sessions per user
    private final Map<Long, EmbedSession> activeSessions = new ConcurrentHashMap<>();

    public PudelMessagePlugin() {
        super("Pudel Embed", "1.0.0", "Zazalng",
                "Interactive embed builder with live preview");
    }

    @Override
    protected void setup() {
        // Main command: !buildembed or !be
        command(this::handleBuildEmbed, "buildembed", "be");
    }

    private void handleBuildEmbed(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.reply("❌ This command can only be used in a server!");
            return;
        }

        long userId = ctx.getUser().getIdLong();

        // Initial command with no args - create new session
        if (!ctx.hasArgs()) {
            createNewSession(ctx, userId);
            return;
        }

        // Get or create session
        EmbedSession session = activeSessions.get(userId);
        if (session == null) {
            createNewSession(ctx, userId);
            session = activeSessions.get(userId);
        }

        // Parse command arguments
        String subCommand = ctx.getArg(0).toLowerCase();
        String value = ctx.getArgCount() > 1 ?
                ctx.getArgsString().substring(subCommand.length()).trim() : "";

        // Delete the user's command message
        ctx.getMessage().delete().queue(null, e -> {});

        // Process subcommand
        processSubCommand(ctx, session, subCommand, value);
    }

    private void createNewSession(CommandContext ctx, long userId) {
        // Delete old session message if exists
        EmbedSession oldSession = activeSessions.get(userId);
        if (oldSession != null && oldSession.previewMessage != null) {
            oldSession.previewMessage.delete().queue(null, e -> {});
        }

        // Create new session
        EmbedSession session = new EmbedSession(userId);
        activeSessions.put(userId, session);

        // Send initial preview
        EmbedBuilder preview = buildPreview(session);
        ctx.getChannel().sendMessageEmbeds(preview.build()).queue(msg -> {
            session.previewMessage = msg;
        });

        // Delete user's command after a moment
        ctx.getMessage().delete().queueAfter(1, java.util.concurrent.TimeUnit.SECONDS,
                null, e -> {});
    }

    private void processSubCommand(CommandContext ctx,
                                   EmbedSession session, String subCommand, String value) {
        try {
            switch (subCommand) {
                case "title":
                    if (value.isEmpty()) {
                        session.title = null;
                    } else if (value.length() > 256) {
                        sendError(ctx, "Title must be 256 characters or less!");
                        return;
                    } else {
                        session.title = value;
                    }
                    break;

                case "desc":
                case "description":
                    if (value.isEmpty()) {
                        session.description = null;
                    } else if (value.length() > 4096) {
                        sendError(ctx, "Description must be 4096 characters or less!");
                        return;
                    } else {
                        session.description = value;
                    }
                    break;

                case "thumbnail":
                case "thumb":
                    if (value.isEmpty()) {
                        session.thumbnail = null;
                    } else if (!isValidUrl(value)) {
                        sendError(ctx, "Invalid URL for thumbnail!");
                        return;
                    } else {
                        session.thumbnail = value;
                    }
                    break;

                case "image":
                case "img":
                    if (value.isEmpty()) {
                        session.image = null;
                    } else if (!isValidUrl(value)) {
                        sendError(ctx, "Invalid URL for image!");
                        return;
                    } else {
                        session.image = value;
                    }
                    break;

                case "footer":
                    if (value.isEmpty()) {
                        session.footer = null;
                    } else if (value.length() > 2048) {
                        sendError(ctx, "Footer must be 2048 characters or less!");
                        return;
                    } else {
                        session.footer = value;
                    }
                    break;

                case "author":
                    if (value.isEmpty()) {
                        session.author = null;
                    } else if (value.length() > 256) {
                        sendError(ctx, "Author must be 256 characters or less!");
                        return;
                    } else {
                        session.author = value;
                    }
                    break;

                case "url":
                    if (value.isEmpty()) {
                        session.url = null;
                    } else if (!isValidUrl(value)) {
                        sendError(ctx, "Invalid URL!");
                        return;
                    } else {
                        session.url = value;
                    }
                    break;

                case "timestamp":
                    if (value.isEmpty()) {
                        session.timestamp = null;
                    } else {
                        session.timestamp = parseTimestamp(value);
                        if (session.timestamp == null) {
                            sendError(ctx, "Invalid timestamp format! Use: DD-MM-YYYY HH:mm:ss+OFFSET");
                            return;
                        }
                    }
                    break;

                case "color":
                case "colour":
                    if (value.isEmpty()) {
                        session.color = null;
                    } else {
                        session.color = parseColor(value);
                        if (session.color == null) {
                            sendError(ctx, "Invalid color! Use hex format (e.g., 26cfd5)");
                            return;
                        }
                    }
                    break;

                case "lineout":
                    if (value.isEmpty()) {
                        sendError(ctx, "Field value cannot be empty!");
                        return;
                    }
                    session.fields.add(new EmbedField(value, false));
                    break;

                case "linein":
                    if (value.isEmpty()) {
                        sendError(ctx, "Field value cannot be empty!");
                        return;
                    }
                    session.fields.add(new EmbedField(value, true));
                    break;

                case "clearfields":
                    session.fields.clear();
                    break;

                case "clear":
                case "reset":
                    session.reset();
                    break;

                case "build":
                    buildAndPost(ctx, session, value);
                    return; // Don't update preview, we're done

                default:
                    sendError(ctx, "Unknown command: " + subCommand);
                    return;
            }

            // Update preview
            updatePreview(session);

        } catch (Exception e) {
            sendError(ctx, "Error: " + e.getMessage());
            log("error", "Error processing embed command", e);
        }
    }

    private void buildAndPost(CommandContext ctx,
                              EmbedSession session, String channelRef) {
        if (channelRef.isEmpty()) {
            sendError(ctx, "Please specify a channel! Usage: !be build #channel");
            return;
        }

        // Parse channel
        TextChannel targetChannel = null;

        // Check for channel mention
        Pattern mentionPattern = Pattern.compile("<#(\\d+)>");
        Matcher matcher = mentionPattern.matcher(channelRef);
        if (matcher.find()) {
            String channelId = matcher.group(1);
            targetChannel = ctx.getGuild().getTextChannelById(channelId);
        } else {
            // Try as channel ID
            try {
                targetChannel = ctx.getGuild().getTextChannelById(channelRef);
            } catch (Exception e) {
                // Try as channel name
                List<TextChannel> channels = ctx.getGuild().getTextChannelsByName(channelRef, true);
                if (!channels.isEmpty()) {
                    targetChannel = channels.get(0);
                }
            }
        }

        if (targetChannel == null) {
            sendError(ctx, "Channel not found!");
            return;
        }

        // Check permissions
        if (!ctx.getGuild().getSelfMember().hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            sendError(ctx, "I don't have permission to send messages in that channel!");
            return;
        }

        if (!ctx.getMember().hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            sendError(ctx, "You don't have permission to send messages in that channel!");
            return;
        }

        // Build the final embed
        MessageEmbed finalEmbed = buildFinalEmbed(session);

        // Post to target channel
        TextChannel finalTargetChannel = targetChannel;
        targetChannel.sendMessageEmbeds(finalEmbed).queue(
                _ -> {
                    // Success - delete preview and session
                    if (session.previewMessage != null) {
                        session.previewMessage.delete().queue(null, e -> {});
                    }
                    activeSessions.remove(session.userId);

                    // Send confirmation
                    ctx.getChannel().sendMessage("✅ Embed posted to " + finalTargetChannel.getAsMention() + "!")
                            .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
                },
                error -> {
                    sendError(ctx, "Failed to post embed: " + error.getMessage());
                }
        );
    }

    private EmbedBuilder buildPreview(EmbedSession session) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🔨 Embed Builder - Preview", null);
        builder.setDescription("**Use these commands to build your embed:**\n\n" +
                "`!be title <text>` - Set title\n" +
                "`!be desc <text>` - Set description\n" +
                "`!be color <hex>` - Set color (e.g., 26cfd5)\n" +
                "`!be thumbnail <url>` - Set thumbnail\n" +
                "`!be image <url>` - Set image\n" +
                "`!be author <text>` - Set author\n" +
                "`!be footer <text>` - Set footer\n" +
                "`!be url <url>` - Set title URL\n" +
                "`!be timestamp <date>` - Set timestamp (DD-MM-YYYY HH:mm:ss+OFFSET)\n" +
                "`!be lineout <text>` - Add field (not inline)\n" +
                "`!be linein <text>` - Add field (inline)\n" +
                "`!be clearfields` - Clear all fields\n" +
                "`!be reset` - Reset everything\n" +
                "`!be build #channel` - Post to channel\n\n" +
                "**Current Preview:**");

        builder.setColor(Color.BLUE);

        if (session.title != null || session.description != null || !session.fields.isEmpty()) {
            builder.addField("", "━━━━━━━━━━━━━━━━━━━━", false);
            if (session.title != null) {
                builder.addField("Title", session.title, false);
            }
            if (session.description != null) {
                builder.setDescription("Description");
            }
            if (session.color != null) {
                builder.addField("Color", "#" + toHex(session.color), true);
            }
            if (session.author != null) {
                builder.addField("Author", session.author, true);
            }
            if (session.footer != null) {
                builder.addField("Footer", session.footer, true);
            }
            if (session.thumbnail != null) {
                builder.addField("Thumbnail", "✅ Set", true);
            }
            if (session.image != null) {
                builder.addField("Image", "✅ Set", true);
            }
            if (session.timestamp != null) {
                builder.addField("Timestamp", "✅ Set", true);
            }
            if (!session.fields.isEmpty()) {
                builder.addField("Fields", session.fields.size() + " field(s) added", false);
            }
        }

        builder.setFooter("Embed Builder by " + session.userId);
        builder.setTimestamp(OffsetDateTime.now());

        return builder;
    }

    private MessageEmbed buildFinalEmbed(EmbedSession session) {
        EmbedBuilder builder = new EmbedBuilder();

        if (session.title != null) {
            builder.setTitle(session.title, session.url);
        }
        if (session.description != null) {
            builder.setDescription(session.description);
        }
        if (session.color != null) {
            builder.setColor(session.color);
        }
        if (session.author != null) {
            builder.setAuthor(session.author);
        }
        if (session.footer != null) {
            builder.setFooter(session.footer);
        }
        if (session.thumbnail != null) {
            builder.setThumbnail(session.thumbnail);
        }
        if (session.image != null) {
            builder.setImage(session.image);
        }
        if (session.timestamp != null) {
            builder.setTimestamp(session.timestamp);
        }

        for (EmbedField field : session.fields) {
            builder.addField("", field.value, field.inline);
        }

        return builder.build();
    }

    private void updatePreview(EmbedSession session) {
        if (session.previewMessage != null) {
            EmbedBuilder preview = buildPreview(session);
            session.previewMessage.editMessageEmbeds(preview.build()).queue(null, e -> {});
        }
    }

    private void sendError(CommandContext ctx, String message) {
        ctx.getChannel().sendMessage("❌ " + message)
                .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
    }

    private boolean isValidUrl(String url) {
        try {
            new URI(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    private Color parseColor(String hex) {
        try {
            // Remove # if present
            hex = hex.replace("#", "");
            if (hex.length() != 6) return null;
            return Color.decode("#" + hex);
        } catch (Exception e) {
            return null;
        }
    }

    private String toHex(Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }

        try {
            // Regex Explanation:
            // ^                        : Start of line
            // (?: ... )?               : Non-capturing group for OPTIONAL DATE
            //   (\d+)                  : Group 1: First date part (Day or Year)
            //   [-!@#$%^&*/.]+         : Separator (any of these symbols, one or more)
            //   (\d+)                  : Group 2: Month
            //   [-!@#$%^&*/.]+         : Separator
            //   (\d+)                  : Group 3: Last date part (Year or Day)
            //   \s+                    : Whitespace separating date and time
            // (\d{2}):(\d{2}):(\d{2})  : Groups 4,5,6: HH:mm:ss (Strictly uses :)
            // ([+-]\d+)                : Group 7: Offset (e.g., +7, -05)
            String regex = "^(?:(\\d+)[-!@#$%^&*/.]+(\\d+)[-!@#$%^&*/.]+(\\d+)\\s+)?(\\d{2}):(\\d{2}):(\\d{2})([+-]\\d+)$";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(timestamp.trim());

            if (!matcher.matches()) {
                // Log error or handle invalid format
                return null;
            }

            // --- 1. Handle Date ---
            int year, month, day;

            // If Group 1 is null, the Date part was not provided -> Use Today
            if (matcher.group(1) == null) {
                LocalDate today = LocalDate.now();
                year = today.getYear();
                month = today.getMonthValue();
                day = today.getDayOfMonth();
            } else {
                int p1 = Integer.parseInt(matcher.group(1));
                int p2 = Integer.parseInt(matcher.group(2));
                int p3 = Integer.parseInt(matcher.group(3));

                // Smart Logic: Detect if YYYY-MM-DD or DD-MM-YYYY
                // If the first part is 4 digits (or simply > 31), it must be the Year.
                if (p1 > 31) {
                    // Format: yyyy-MM-dd
                    year = p1;
                    month = p2;
                    day = p3;
                } else {
                    // Format: dd-MM-yyyy
                    day = p1;
                    month = p2;
                    year = p3;
                }
            }

            // --- 2. Handle Time ---
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));
            int second = Integer.parseInt(matcher.group(6));

            // --- 3. Handle Offset ---
            int offsetHours = Integer.parseInt(matcher.group(7));

            return OffsetDateTime.of(year, month, day, hour, minute, second, 0,
                    ZoneOffset.ofHours(offsetHours));

        } catch (Exception e) {
            // Handle parsing errors (e.g., Month 13, Day 32)
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onDisabled() {
        // Clean up all sessions
        for (EmbedSession session : activeSessions.values()) {
            if (session.previewMessage != null) {
                session.previewMessage.delete().queue(null, e -> {});
            }
        }
        activeSessions.clear();
    }

    // Session class to track embed building state
    private static class EmbedSession {
        final long userId;
        Message previewMessage;
        String title;
        String description;
        Color color;
        String thumbnail;
        String image;
        String author;
        String footer;
        String url;
        OffsetDateTime timestamp;
        List<EmbedField> fields = new ArrayList<>();

        EmbedSession(long userId) {
            this.userId = userId;
        }

        void reset() {
            title = null;
            description = null;
            color = null;
            thumbnail = null;
            image = null;
            author = null;
            footer = null;
            url = null;
            timestamp = null;
            fields.clear();
        }
    }

    // Field class
        private record EmbedField(String value, boolean inline) {
    }
}