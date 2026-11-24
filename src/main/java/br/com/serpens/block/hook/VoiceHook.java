package br.com.serpens.block.hook;

import java.util.UUID;

import br.com.serpens.block.SerpensBlockPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;

/**
 * Simple Voice Chat: drops each audio packet a listener would receive from someone they
 * blocked. Covers proximity, groups and spectator audio. Only loaded when voicechat is enabled.
 */
public final class VoiceHook implements VoicechatPlugin {

    private final SerpensBlockPlugin plugin;
    /** Voice chat has no unregister; a disabled hook just stops acting. */
    private volatile boolean active = true;

    private VoiceHook(SerpensBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return the hook, or null if the voice chat service was not found */
    public static VoiceHook register(SerpensBlockPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            return null;
        }
        VoiceHook hook = new VoiceHook(plugin);
        service.registerPlugin(hook);
        return hook;
    }

    public void disable() {
        active = false;
    }

    @Override
    public String getPluginId() {
        return "serpensblock";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(EntitySoundPacketEvent.class, this::onSound);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onSound);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onSound);
    }

    private void onSound(SoundPacketEvent<?> event) {
        if (!active || event.isCancelled()) {
            return;
        }
        UUID speaker = uuidOf(event.getSenderConnection());
        UUID listener = uuidOf(event.getReceiverConnection());
        if (speaker != null && listener != null && plugin.policy().muteVoice(listener, speaker)) {
            event.cancel();
        }
    }

    private static UUID uuidOf(VoicechatConnection connection) {
        return connection == null || connection.getPlayer() == null ? null : connection.getPlayer().getUuid();
    }
}
