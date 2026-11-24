package br.com.serpens.block.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import br.com.serpens.block.BlockPolicy;
import br.com.serpens.block.SerpensBlockPlugin;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.CarbonEventHandler;
import net.draycia.carbon.api.event.CarbonEventSubscription;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.draycia.carbon.api.event.events.CarbonPrivateChatEvent;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.kyori.adventure.audience.Audience;

/** Public chat and private messages through CarbonChat. Only loaded when CarbonChat is enabled. */
public final class CarbonHook {

    private final SerpensBlockPlugin plugin;
    private final List<CarbonEventSubscription<?>> subscriptions = new ArrayList<>();
    private boolean warnedImmutable;

    public CarbonHook(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        CarbonEventHandler events = CarbonChatProvider.carbonChat().eventHandler();
        subscriptions.add(events.subscribe(CarbonChatEvent.class, this::onChat));
        subscriptions.add(events.subscribe(CarbonPrivateChatEvent.class, this::onPrivate));
    }

    public void unregister() {
        subscriptions.forEach(CarbonEventSubscription::dispose);
        subscriptions.clear();
    }

    private void onChat(CarbonChatEvent event) {
        if (event.cancelled()) {
            return;
        }
        UUID sender = event.sender().uuid();
        BlockPolicy policy = plugin.policy();
        try {
            event.recipients().removeIf(a -> {
                UUID viewer = uuidOf(a);
                return viewer != null && policy.hideChat(viewer, sender);
            });
        } catch (UnsupportedOperationException e) {
            if (!warnedImmutable) {
                warnedImmutable = true;
                plugin.getLogger().warning("CarbonChat recipients list is read-only in this version; chat blocking is disabled.");
            }
        }
    }

    private void onPrivate(CarbonPrivateChatEvent event) {
        if (event.cancelled() || !plugin.settings().privateMessages) {
            return;
        }
        CarbonPlayer sender = event.sender();
        CarbonPlayer recipient = event.recipient();
        BlockPolicy.Contact contact = plugin.policy().contact(sender.uuid(), recipient.uuid());
        if (contact != BlockPolicy.Contact.OK) {
            event.cancelled(true);
            plugin.settings().send(sender, BlockPolicy.messageKey(contact), recipient.username());
        }
    }

    private static UUID uuidOf(Audience audience) {
        if (audience instanceof CarbonPlayer cp) {
            return cp.uuid();
        }
        if (audience instanceof Player p) {
            return p.getUniqueId();
        }
        return null;
    }
}
