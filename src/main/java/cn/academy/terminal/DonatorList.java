package cn.academy.terminal;

import cn.academy.AcademyCraft;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Original public donor endpoint and response format; failures leave the bundled fallback available. */
public enum DonatorList {
    Instance;

    private List<String> donators = List.of();
    private final AtomicBoolean requesting = new AtomicBoolean();
    private record Attributes(List<String> list) {}
    private record ResponseData(boolean success, Attributes attributes) {}

    public boolean isLoaded() { return !donators.isEmpty(); }
    public List<String> getList() { return donators; }

    public void tryRequest() {
        if (isLoaded() || !requesting.compareAndSet(false, true)) return;
        Util.nonCriticalIoPool().execute(() -> {
            try {
                var connection = URI.create("https://ac.li-dev.cn/donators").toURL().openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(5_000);
                try (var reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    var response = new Gson().fromJson(reader, ResponseData.class);
                    if (response == null || !response.success || response.attributes == null || response.attributes.list == null)
                        throw new IllegalArgumentException("Invalid donor-list response");
                    var received = List.copyOf(response.attributes.list);
                    Minecraft.getInstance().execute(() -> {
                        donators = received;
                        NeoForge.EVENT_BUS.post(new DonatorListRefreshEvent());
                    });
                }
            } catch (Exception exception) {
                AcademyCraft.LOGGER.debug("Cannot refresh donor list; retaining bundled fallback", exception);
            } finally { requesting.set(false); }
        });
    }

    public static final class DonatorListRefreshEvent extends Event {}
}
