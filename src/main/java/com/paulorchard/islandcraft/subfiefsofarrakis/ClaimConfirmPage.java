package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** "Claim this chunk?" with Confirm and Cancel. Closing the page any other way counts as Cancel. */
public class ClaimConfirmPage extends InteractiveCustomUIPage<ClaimConfirmPage.PageData> {

    private static final String PAGE = "Pages/Subfiefs_of_Arrakis/Arrakis_Subfief_Claim_Confirm.ui";
    private static final String LANG = "server.subfiefsOfArrakis.claim.";

    public enum Action {
        Confirm,
        Cancel
    }

    public static class PageData {

        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", new EnumCodec<>(Action.class, EnumCodec.EnumStyle.LEGACY), false),
                        (data, value) -> data.action = value,
                        data -> data.action)
                .add()
                .build();

        public Action action;
    }

    private final PlayerRef player;
    private final ClaimService service;
    private final int chunkX;
    private final int chunkZ;
    private final Claim previous;

    public ClaimConfirmPage(PlayerRef player, ClaimService service, int chunkX, int chunkZ, Claim previous) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.player = player;
        this.service = service;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.previous = previous;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(PAGE);
        commands.set("#Question.TextSpans", Message.translation(LANG + "question"));
        commands.set("#Location.TextSpans", Message.translation(LANG + "location").param("x", chunkX).param("z", chunkZ));
        commands.set("#Warning.TextSpans", previous == null
                ? Message.empty()
                : Message.translation(LANG + "releaseWarning").param("x", previous.chunkX()).param("z", previous.chunkZ()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
                new EventData().append("Action", Action.Confirm.name()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                new EventData().append("Action", Action.Cancel.name()));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        if (data.action == Action.Confirm) {
            service.confirm(ref, store, player);
        } else {
            service.cancel(ref, store, player);
        }
    }

    /** Escape or any other way of closing the page: nothing was claimed. */
    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (service.abandon(player.getUuid())) {
            player.sendMessage(Message.translation(LANG + "cancelled"));
        }
    }
}
