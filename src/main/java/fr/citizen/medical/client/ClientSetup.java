package fr.citizen.medical.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.citizen.medical.CitizenMedical;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private ClientSetup() { }

    public static final KeyMapping CURSOR_KEY = new KeyMapping("key.citizenmedical.cursor",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.citizenmedical");

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(CURSOR_KEY));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientState.reset());
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(CitizenMedical.MOD_ID, "wound_hud"), (g, delta) -> renderHud(g)));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (CURSOR_KEY.consumeClick()) {
            // Si Citizen Admin a deja ouvert son curseur sur la meme touche, on ne double pas l'ecran.
            if (mc.screen == null && mc.player != null && ClientState.doctor) mc.setScreen(new MedicalCursorScreen());
        }
        // Joueurs a terre : affiches allonges (sauf s'ils sont portes).
        if (mc.level != null) {
            for (net.minecraft.world.entity.player.Player pl : mc.level.players()) {
                boolean down = ClientState.downed.contains(pl.getUUID()) && !pl.isPassenger();
                if (down) {
                    if (pl.getForcedPose() != net.minecraft.world.entity.Pose.SWIMMING) pl.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
                    FORCED.add(pl.getUUID());
                } else if (FORCED.remove(pl.getUUID()) && pl.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) {
                    pl.setForcedPose(null);
                }
            }
        }
        if (mc.player != null && ClientState.selfDowned() && mc.player.isSprinting()) mc.player.setSprinting(false);
        // Un joueur qui boite ne peut pas courir.
        if (mc.player != null && ClientState.limping() && mc.player.isSprinting()) mc.player.setSprinting(false);
    }

    private static final java.util.Set<java.util.UUID> FORCED = new java.util.HashSet<>();

    private static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && mc.player != null && ClientState.selfDowned()) {
            int w = g.guiWidth(), h = g.guiHeight();
            g.fill(0, 0, w, h, 0x55400000);
            String t = "✚ À TERRE";
            String t2 = mc.player.isPassenger() ? "Vous êtes porté jusqu'à l'hôpital…" : "Attendez qu'on vous porte, ou le transport à l'hôpital";
            g.drawCenteredString(mc.font, t, w / 2, h / 2 - 20, 0xFFE05050);
            g.drawCenteredString(mc.font, t2, w / 2, h / 2 - 6, 0xFFEADBC0);
        }
        if (mc.options.hideGui || mc.player == null || ClientState.self == null) return;
        var s = ClientState.self;
        int elapsed = (int) ((System.currentTimeMillis() - ClientState.selfAt) / 1000L);
        String l1 = "✚ " + s.cause();
        String l2;
        int color;
        if (s.treated()) {
            int left = Math.max(0, s.limpMinLeftSec() - elapsed);
            l2 = "Soigné — vous boitez encore " + fmt(left);
            color = 0xFF9FD86B;
        } else {
            int next = Math.max(0, s.nextBleedSec() - elapsed);
            l2 = (s.bandaged() ? "Bandé — -½ ❤ dans " : "Saigne — -1 ❤ dans ") + fmt(next) + (s.bandaged() ? " · voyez un médecin" : " · posez un bandage !");
            color = s.bandaged() ? 0xFFE8C860 : 0xFFE05050;
        }
        int w = Math.max(mc.font.width(l1), mc.font.width(l2)) + 10;
        int x = 4, y = 4;
        g.fill(x, y, x + w, y + 24, 0xB0140E0A);
        g.fill(x, y + 23, x + w, y + 24, 0xFFB8963E);
        g.drawString(mc.font, l1, x + 5, y + 3, 0xFFEADBC0, false);
        g.drawString(mc.font, l2, x + 5, y + 13, color, false);
    }

    static String fmt(int sec) {
        return (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
    }
}
