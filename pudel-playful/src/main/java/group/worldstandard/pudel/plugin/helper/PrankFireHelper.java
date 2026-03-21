package group.worldstandard.pudel.plugin.helper;

import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.entity.PrankCollection;
import group.worldstandard.pudel.plugin.entity.PrankContainer;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Handles prank firing logic — quick-fire modal, direct fire, and modal-based fire.
 */
public class PrankFireHelper {

    private final PluginRepository<PrankContainer> containerRepo;
    private final PluginRepository<PrankCollection> collectionRepo;
    private final Map<String, Message> controlMessages;
    private final Map<String, String> viewingContainer;
    private final Random random;
    private final String modalPrefix;
    private final Color accentPrank;
    private final PrankPanelBuilder panelBuilder;

    public PrankFireHelper(PluginRepository<PrankContainer> containerRepo,
                           PluginRepository<PrankCollection> collectionRepo,
                           Map<String, Message> controlMessages,
                           Map<String, String> viewingContainer,
                           Random random,
                           String modalPrefix,
                           Color accentPrank,
                           PrankPanelBuilder panelBuilder) {
        this.containerRepo = containerRepo;
        this.collectionRepo = collectionRepo;
        this.controlMessages = controlMessages;
        this.viewingContainer = viewingContainer;
        this.random = random;
        this.modalPrefix = modalPrefix;
        this.accentPrank = accentPrank;
        this.panelBuilder = panelBuilder;
    }

    /**
     * Shows the fire-select modal with container list and optional target user.
     */
    public void showFireSelectModal(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        if (myContainers.isEmpty()) {
            openControlPanel(event);
            return;
        }

        StringSelectMenu.Builder containerMenu = StringSelectMenu.create("container-select")
                .setPlaceholder("🎯 Pick a container to fire…");

        for (PrankContainer c : myContainers) {
            long prankCount = collectionRepo.query()
                    .where("container_id", c.getContainerId())
                    .count();
            containerMenu.addOption(
                    c.getName() + " (" + prankCount + " pranks)",
                    c.getContainerId(),
                    "Used " + c.getUsage() + " times"
            );
        }

        containerMenu.addOption("⚙️ Open Control Panel", "open-control-panel",
                "Manage your prank containers");

        EntitySelectMenu targetMenu = EntitySelectMenu.create("target-select", EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder("👤 Select a target (optional)")
                .setRequired(false)
                .setMaxValues(1)
                .build();

        event.replyModal(Modal.create(modalPrefix + "fire-select", "🎭 Quick Prank")
                .addComponents(
                        Label.of("📦 Container", containerMenu.build()),
                        Label.of("🎯 Target User (optional)", targetMenu)
                )
                .build()
        ).queue();
    }

    /**
     * Handles the fire-select modal response.
     */
    public void handleFireSelectModal(ModalInteractionEvent event, String userId) {
        var containerMapping = event.getValue("container-select");
        if (containerMapping == null || containerMapping.getAsStringList().isEmpty()) {
            openControlPanelFromModal(event, userId);
            return;
        }

        String selectedContainerId = containerMapping.getAsStringList().getFirst();

        if ("open-control-panel".equals(selectedContainerId)) {
            openControlPanelFromModal(event, userId);
            return;
        }

        PrankContainer container = findContainerById(selectedContainerId);
        if (container == null || !container.getUserId().equals(userId)) {
            openControlPanelFromModal(event, userId);
            return;
        }

        User target = null;
        var targetMapping = event.getValue("target-select");
        if (targetMapping != null && !targetMapping.getAsStringList().isEmpty()) {
            String targetId = targetMapping.getAsStringList().getFirst();
            target = event.getJDA().getUserById(targetId);
        }

        fireModalPrank(event, container, target);
    }

    /**
     * Fires a prank directly from a slash command with name and optional target.
     */
    public void firePrank(SlashCommandInteractionEvent event, String name, User target) {
        List<PrankContainer> containers = containerRepo.query()
                .where("user_id", event.getUser().getId())
                .where("name", name)
                .list();

        if (containers.isEmpty()) {
            event.reply("❌ No prank container named **" + name + "** found!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PrankContainer container = containers.getFirst();
        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", container.getContainerId())
                .list();

        if (pranks.isEmpty()) {
            event.reply("❌ Container **" + name + "** is empty — add some pranks first!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PrankCollection prank = pranks.get(random.nextInt(pranks.size()));
        String message = buildPrankMessage(prank, event.getUser(), target);

        container.setUsage(container.getUsage() + 1);
        containerRepo.save(container);

        event.reply(buildPrankReply(message, prank, container).build()).queue();
    }

    // ==================== PRIVATE HELPERS ====================

    private void fireModalPrank(ModalInteractionEvent event, PrankContainer container, User target) {
        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", container.getContainerId())
                .list();

        if (pranks.isEmpty()) {
            event.reply("❌ Container **" + container.getName() + "** is empty — add some pranks first!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PrankCollection prank = pranks.get(random.nextInt(pranks.size()));
        String message = buildPrankMessage(prank, event.getUser(), target);

        container.setUsage(container.getUsage() + 1);
        containerRepo.save(container);

        event.reply(buildPrankReply(message, prank, container).build()).queue();
    }

    private String buildPrankMessage(PrankCollection prank, User invoker, User target) {
        String invokerMention = invoker.getAsMention();
        String targetMention = target != null ? target.getAsMention() : "";
        return prank.getPlaceholder()
                .replaceAll("%m", invokerMention)
                .replaceAll("%t", targetMention);
    }

    private MessageCreateBuilder buildPrankReply(String message, PrankCollection prank, PrankContainer container) {
        Container prankCard;

        if(message.isEmpty()){
            prankCard = Container.of(
                    MediaGallery.of(MediaGalleryItem.fromUrl(prank.getUrl())),
                    Separator.create(false, Separator.Spacing.SMALL),
                    TextDisplay.of("-# 📦 " + container.getName() + " • Used " + container.getUsage() + " times")
            ).withAccentColor(accentPrank);
        } else {
            prankCard = Container.of(
                    TextDisplay.of(message),
                    MediaGallery.of(MediaGalleryItem.fromUrl(prank.getUrl())),
                    Separator.create(false, Separator.Spacing.SMALL),
                    TextDisplay.of("-# 📦 " + container.getName() + " • Used " + container.getUsage() + " times")
            ).withAccentColor(accentPrank);
        }

        return new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(prankCard);
    }

    private void openControlPanel(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        Message oldMsg = controlMessages.remove(userId);
        if (oldMsg != null) {
            try { oldMsg.delete().queue(null, _ -> {}); } catch (Exception _) {}
        }
        viewingContainer.remove(userId);

        event.reply(panelBuilder.buildMainPanel(userId).build())
                .setEphemeral(true)
                .queue(hook -> hook.retrieveOriginal().queue(msg -> controlMessages.put(userId, msg)));
    }

    private void openControlPanelFromModal(ModalInteractionEvent event, String userId) {
        Message oldMsg = controlMessages.remove(userId);
        if (oldMsg != null) {
            try { oldMsg.delete().queue(null, _ -> {}); } catch (Exception _) {}
        }
        viewingContainer.remove(userId);

        event.reply(panelBuilder.buildMainPanel(userId).build())
                .setEphemeral(true)
                .queue(hook -> hook.retrieveOriginal().queue(msg -> controlMessages.put(userId, msg)));
    }

    private PrankContainer findContainerById(String containerId) {
        List<PrankContainer> list = containerRepo.query()
                .where("container_id", containerId)
                .list();
        return list.isEmpty() ? null : list.getFirst();
    }
}

