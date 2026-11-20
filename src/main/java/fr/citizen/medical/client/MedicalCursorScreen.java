package fr.citizen.medical.client;

import fr.citizen.medical.net.RequestWoundsPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Optional;

/** Mode medecin (touche Alt) : souris libre, clic droit sur un joueur = examen medical. */
public class MedicalCursorScreen extends Screen {
    private static final double RANGE = 32.0;
    private Player hovered;

    public MedicalCursorScreen() { super(Component.literal("Mode médecin")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // monde visible, pas de flou
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hovered = pick(mouseX, mouseY);
        String hint = "✚ Mode médecin — clic droit sur un joueur : examiner · Alt / Échap pour quitter";
        int w = font.width(hint);
        int hy = height - 72;
        g.fill(width / 2 - w / 2 - 6, hy, width / 2 + w / 2 + 6, hy + 16, 0xC0140E0A);
        g.fill(width / 2 - w / 2 - 6, hy + 15, width / 2 + w / 2 + 6, hy + 16, 0xFFB83A3A);
        g.drawString(font, hint, width / 2 - w / 2, hy + 4, 0xFFE8D8A8, false);
        if (hovered != null) {
            g.renderTooltip(font, Component.literal("§c✚ §6" + hovered.getGameProfile().getName() + " §7— clic droit : examiner"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            Player p = pick(mouseX, mouseY);
            if (p != null) {
                PacketDistributor.sendToServer(new RequestWoundsPayload(p.getUUID()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientSetup.CURSOR_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Rayon partant de la camera et passant par le curseur, puis joueur le plus proche touche. */
    private Player pick(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 origin = cam.getPosition();
        Vector3f look = new Vector3f(cam.getLookVector());
        Vector3f up = new Vector3f(cam.getUpVector());
        Vector3f left = new Vector3f(up).cross(look);
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) width / Math.max(1, height);
        double nx = (2.0 * mouseX / width) - 1.0;
        double ny = 1.0 - (2.0 * mouseY / height);
        Vec3 dir = new Vec3(
                look.x() + up.x() * ny * tanHalf - left.x() * nx * tanHalf * aspect,
                look.y() + up.y() * ny * tanHalf - left.y() * nx * tanHalf * aspect,
                look.z() + up.z() * ny * tanHalf - left.z() * nx * tanHalf * aspect).normalize();
        Vec3 end = origin.add(dir.scale(RANGE));
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            AABB box = p.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = box.clip(origin, end);
            if (box.contains(origin)) hit = Optional.of(origin);
            if (hit.isPresent()) {
                double dist = hit.get().distanceToSqr(origin);
                if (dist < bestDist) { bestDist = dist; best = p; }
            }
        }
        return best;
    }
}
