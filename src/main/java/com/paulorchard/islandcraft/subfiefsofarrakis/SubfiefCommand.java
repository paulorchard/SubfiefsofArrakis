package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Comparator;
import java.util.List;

/**
 * /subfief                    what the chunk you stand in is
 * /subfief give [player]      a claim statue (permission islandcraft.subfiefs.give)
 * /subfief release name       free someone's claim (permission islandcraft.subfiefs.admin)
 * /subfief list               every claim (permission islandcraft.subfiefs.admin)
 */
public class SubfiefCommand extends AbstractPlayerCommand {

    public static final String GIVE_PERMISSION = "islandcraft.subfiefs.give";
    public static final String ADMIN_PERMISSION = "islandcraft.subfiefs.admin";

    private static final String LANG = "server.commands.subfief.";

    private final ClaimRegistry registry;

    public SubfiefCommand(ClaimRegistry registry, ClaimService service) {
        super("subfief", LANG + "desc");
        this.registry = registry;
        addSubCommand(new GiveCommand());
        addSubCommand(new ReleaseCommand(service));
        addSubCommand(new ListCommand(registry));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d position = transform.getPosition();
        int chunkX = ChunkUtil.chunkCoordinate(position.x());
        int chunkZ = ChunkUtil.chunkCoordinate(position.z());
        Claim claim = registry.at(world.getName(), chunkX, chunkZ);
        if (claim == null) {
            context.sendMessage(Message.translation(LANG + "info.free")
                    .param("x", chunkX).param("z", chunkZ).param("world", world.getName()));
            return;
        }
        boolean yours = claim.ownerUuid().equals(playerRef.getUuid());
        context.sendMessage(Message.translation(LANG + (yours ? "info.yours" : "info.owned"))
                .param("x", chunkX).param("z", chunkZ).param("world", world.getName())
                .param("owner", claim.ownerName()).param("time", claim.claimedAt().toString()));
    }

    private static void give(PlayerRef target, CommandContext context) {
        Ref<EntityStore> ref = target.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player.giveItem(new ItemStack(SubfiefsOfArrakisPlugin.STATUE_ID, 1), ref, store);
            target.sendMessage(Message.translation(LANG + "give.received"));
        });
        context.sendMessage(Message.translation(LANG + "give.done").param("player", target.getUsername()));
    }

    /** /subfief give: the statue for yourself. */
    private static class GiveCommand extends AbstractPlayerCommand {

        GiveCommand() {
            super("give", LANG + "give.desc");
            requirePermission(GIVE_PERMISSION);
            addUsageVariant(new GiveToCommand());
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            give(playerRef, context);
        }
    }

    /** /subfief give player: works from the console too. */
    private static class GiveToCommand extends CommandBase {

        private final RequiredArg<PlayerRef> playerArg;

        GiveToCommand() {
            super(LANG + "give.desc");
            requirePermission(GIVE_PERMISSION);
            playerArg = withRequiredArg("player", LANG + "give.player.desc", ArgTypes.PLAYER_REF);
        }

        @Override
        protected void executeSync(CommandContext context) {
            give(playerArg.get(context), context);
        }
    }

    private static class ReleaseCommand extends CommandBase {

        private final ClaimService service;
        private final RequiredArg<String> playerArg;

        ReleaseCommand(ClaimService service) {
            super("release", LANG + "release.desc");
            this.service = service;
            requirePermission(ADMIN_PERMISSION);
            playerArg = withRequiredArg("player", LANG + "release.player.desc", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            String name = playerArg.get(context);
            Claim target = service.claims().stream()
                    .filter(c -> c.ownerName().equalsIgnoreCase(name)).findFirst().orElse(null);
            Claim released = target == null ? null : service.release(target.ownerUuid());
            if (released == null) {
                context.sendMessage(Message.translation(LANG + "release.none").param("player", name));
                return;
            }
            context.sendMessage(Message.translation(LANG + "release.done").param("player", released.ownerName())
                    .param("x", released.chunkX()).param("z", released.chunkZ()).param("world", released.world()));
        }
    }

    private static class ListCommand extends CommandBase {

        private final ClaimRegistry registry;

        ListCommand(ClaimRegistry registry) {
            super("list", LANG + "list.desc");
            this.registry = registry;
            requirePermission(ADMIN_PERMISSION);
        }

        @Override
        protected void executeSync(CommandContext context) {
            List<Claim> claims = registry.all().stream()
                    .sorted(Comparator.comparing(Claim::ownerName, String.CASE_INSENSITIVE_ORDER)).toList();
            if (claims.isEmpty()) {
                context.sendMessage(Message.translation(LANG + "list.empty"));
                return;
            }
            context.sendMessage(Message.translation(LANG + "list.header").param("count", claims.size()));
            for (Claim claim : claims) {
                context.sendMessage(Message.translation(LANG + "list.line").param("owner", claim.ownerName())
                        .param("world", claim.world()).param("x", claim.chunkX()).param("z", claim.chunkZ())
                        .param("time", claim.claimedAt().toString()));
            }
        }
    }
}
