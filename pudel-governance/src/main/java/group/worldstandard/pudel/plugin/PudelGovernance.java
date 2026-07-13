package group.worldstandard.pudel.plugin;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.ContextMenu;
import group.worldstandard.pudel.api.annotation.OnEnable;
import group.worldstandard.pudel.api.annotation.OnShutdown;
import group.worldstandard.pudel.api.annotation.Plugin;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.TableSchema;
import group.worldstandard.pudel.plugin.entity.CaseCollection;
import group.worldstandard.pudel.plugin.entity.ReportCollection;
import group.worldstandard.pudel.plugin.entity.WarnCollection;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;

@Plugin(
        name = "Pudel's Governance",
        version = "0.0.1-indev",
        author = "Zazalng",
        description = ""
)
public class PudelGovernance {
    // ==================== HANDLER IDS (compile-time, used in annotations) ====================
    private static final String BTN_HANDLER = "button:";
    private static final String MODAL_HANDLER = "modal:";
    private static final String MENU_HANDLER = "menu:";
    private static final String CONTEXT_HANDLER = "context:";

    // ==================== RUNTIME PREFIXED IDS (initialized in onEnable) ====================
    private String btnPrefix;
    private String modalPrefix;
    private String menuPrefix;
    private String contextPrefix;

    // ==================== STATE ====================
    private PluginContext context;
    private PluginRepository<CaseCollection> caseRepo;
    private PluginRepository<ReportCollection> reportRepo;
    private PluginRepository<WarnCollection> warnRepo;

    @OnEnable
    public void onEnable(PluginContext ctx){
        this.context = ctx;
        String prefix = ctx.getDatabaseManager().getSchemaName();
        this.btnPrefix = prefix + BTN_HANDLER;
        this.modalPrefix = prefix + MODAL_HANDLER;
        this.menuPrefix = prefix + MENU_HANDLER;
        initializeDatabase(ctx.getDatabaseManager());
        ctx.log("info", "%s (v%s) has initialized on '%s'".formatted(
                ctx.getInfo().getName(), ctx.getInfo().getVersion(), ctx.getPudel().getUserAgent())
        );
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx){
        return true;
    }

    private void initializeDatabase(PluginDatabaseManager db){
        migrationDatabase(db);
        createRepository(db);
    }

    private void migrationDatabase(PluginDatabaseManager db){
        db.migrate(1, h ->
                {
                    db.createTable(TableSchema.builder("case_investigate")
                            .column("guild_id", ColumnType.STRING, 20, false)
                            .column("user_id", ColumnType.STRING, 20, false)
                            .column("issuer_id", ColumnType.STRING, 20, false)
                            .column("judge_id", ColumnType.STRING, 20, false)
                            .column("report_id", ColumnType.TEXT, false)
                            .column("resolve_type", ColumnType.SMALLINT, false, "0")
                            .index("resolve_type")
                            .uniqueIndex("guild_id", "user_id", "issuer_id", "judge_id")
                            .build()
                    );
                    db.createTable(TableSchema.builder("report_case")
                            .column("guild_id", ColumnType.STRING, 20, false)
                            .column("user_id", ColumnType.STRING, 20, false)
                            .column("issuer_id", ColumnType.STRING, 20, false)
                            .column("message_id", ColumnType.STRING, 20, false)
                            .column("reason", ColumnType.TEXT, true)
                            .uniqueIndex("guild_id", "user_id", "issuer_id", "message_id")
                            .build()
                    );
                    db.createTable(TableSchema.builder("warn")
                            .column("guild_id", ColumnType.STRING, 20, false)
                            .build()
                    );
                }

                );
    }

    private void createRepository(PluginDatabaseManager db){
        caseRepo = db.getRepository("case_investigate", CaseCollection.class);
    }

    @ContextMenu(
            name = "Report",
            type = Command.Type.MESSAGE,
            global = false
    )
    public void onReportMenu(MessageContextInteraction event){

    }
}