package group.worldstandard.pudel.plugin.handler;

import group.worldstandard.pudel.plugin.builder.PanelBuilder;
import group.worldstandard.pudel.plugin.session.SessionManager;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

/**
 * Handles entity select menu interactions with flat dispatch.
 */
public class EntitySelectMenuHandler {

    private final String entityMenuPrefix;
    private final SessionManager sessionManager;
    private final PanelBuilder panelBuilder;

    public EntitySelectMenuHandler(String entityMenuPrefix, SessionManager sessionManager,
                                   PanelBuilder panelBuilder) {
        this.entityMenuPrefix = entityMenuPrefix;
        this.sessionManager = sessionManager;
        this.panelBuilder = panelBuilder;
    }

    public void handle(EntitySelectInteractionEvent event) {
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        var menuId = event.getComponentId().substring(entityMenuPrefix.length());
        var mentions = event.getMentions();
        var userId = member.getId();
        var guildId = guild.getId();

        switch (menuId) {
            case "import_category" -> handleImportCategory(event, userId, guildId, mentions);
            case "import_manager" -> handleImportManager(event, userId, guildId, mentions);
            case "import_default_role" -> handleImportDefaultRole(event, userId, guildId, mentions);
            case "create_manager" -> handleCreateManager(event, userId, guildId, mentions);
            case "create_default_role" -> handleCreateDefaultRole(event, userId, guildId, mentions);
        }
    }

    // ==================== IMPORT HANDLERS ====================

    private void handleImportCategory(EntitySelectInteractionEvent event, String userId,
                                        String guildId, Mentions mentions) {
        var state = sessionManager.getImportFormState(userId);
        if (state != null) {
            state.put("category", mentions.getChannels().getFirst().getId());
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }

    private void handleImportManager(EntitySelectInteractionEvent event, String userId,
                                       String guildId, Mentions mentions) {
        var state = sessionManager.getImportFormState(userId);
        if (state != null) {
            state.put("manager", mentions.getMembers().getFirst().getId());
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }

    private void handleImportDefaultRole(EntitySelectInteractionEvent event, String userId,
                                          String guildId, Mentions mentions) {
        var state = sessionManager.getImportFormState(userId);
        if (state != null) {
            state.put("default_role", mentions.getRoles().getFirst().getId());
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }

    // ==================== CREATE HANDLERS ====================

    private void handleCreateManager(EntitySelectInteractionEvent event, String userId,
                                       String guildId, Mentions mentions) {
        var state = sessionManager.getCreateFormState(userId);
        if (state != null) {
            state.put("manager", mentions.getMembers().getFirst().getId());
            event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
        }
    }

    private void handleCreateDefaultRole(EntitySelectInteractionEvent event, String userId,
                                          String guildId, Mentions mentions) {
        var state = sessionManager.getCreateFormState(userId);
        if (state != null) {
            state.put("default_role", mentions.getRoles().getFirst().getId());
            event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
        }
    }
}