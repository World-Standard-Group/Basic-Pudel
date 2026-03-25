package group.worldstandard.pudel.plugin;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.OnEnable;
import group.worldstandard.pudel.api.annotation.OnShutdown;
import group.worldstandard.pudel.api.annotation.Plugin;
import group.worldstandard.pudel.api.annotation.SlashCommand;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.TableSchema;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.CategorySetting;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(
        name = "Pudel's Category Management",
        author = "Zazalng",
        version = "1.0.0",
        description = "Managing Category by Auto given administrator permission to individual who should manage this category."
)
public class PudelCategorizing {
    // ==================== CONSTANTS ====================
    private static final String BTN = "p_category:button:";
    private static final String MODAL_PREFIX = "p_category:modal:";
    private static final String MENU_PREFIX = "p_category:menu:";

    // ==================== STATE ====================
    private PluginContext context;

    private void initializeDatabase(PluginDatabaseManager db){
        TableSchema category = TableSchema.builder("category")
                .column("guild_id", ColumnType.TEXT, false)
                .column("category_id", ColumnType.TEXT, false)
                .column("manager_id", ColumnType.TEXT, true)
                .column("default_role", ColumnType.TEXT, true)
                .uniqueIndex("category_id")
                .build();

        context.log("info", "Creating Database base on '%s', Resulting: %s".formatted(category.getTableName(), db.createTable(category)));

        category = TableSchema.builder("setting")
                .column("guild_id", ColumnType.TEXT, false)
                .column("permissions", ColumnType.TEXT, false, "")
                .uniqueIndex("guild_id")
                .build();

        context.log("info", "Creating Database base on '%s', Resulting: %s".formatted(category.getTableName(), db.createTable(category)));

        this.category = db.getRepository("category", CategoryEntry.class);
        this.setting = db.getRepository("setting", CategorySetting.class);
    }

    // ==================== Database ====================
    private PluginRepository<CategoryEntry> category;
    private PluginRepository<CategorySetting> setting;

    // ==================== LIFECYCLE ====================
    @OnEnable
    void OnEnable(PluginContext ctx){
        this.context = ctx;
        initializeDatabase(context.getDatabaseManager());
        context.log("info", "%s (v%s) has initialized on '%s'".formatted(context.getInfo().getName(), context.getInfo().getVersion(), context.getPudel().getUserAgent()));
    }

    @OnShutdown
    boolean OnShutdown(PluginContext ctx){
        try{
            context = null;
            System.gc();

            ctx.log("info", "%s (v%s) graceful shutdown plugins on '%s'".formatted(context.getInfo().getName(), context.getInfo().getVersion(), context.getPudel().getUserAgent()));
            return true;
        } catch (Exception e) {
            ctx.log("error", "Unable to shutdown graceful given message of '%s'".formatted(e.getMessage()), e);
            return false;
        }
    }

    // ==================== Logic Plugin ====================

    @SlashCommand(
            name = "categorizement",
            description = "Open Control Panel for management category channel",
            nsfw = false,
            global = false,
            permissions = {Permission.MANAGE_CHANNEL},
            integrationTo = IntegrationType.GUILD_INSTALL,
            integrationContext = {InteractionContextType.GUILD}
    )
    public void onOpenControlPanel(SlashCommandInteractionEvent event){
        Guild guild = event.getGuild();
        Member user = event.getMember();

        if (guild == null || user == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        ChannelAction<Category> c = guild.createCategory().addRolePermissionOverride(guild.getPublicRole().getIdLong(), null, List.of(Permission.VIEW_CHANNEL));

        String userId = user.getId();
    }
}
