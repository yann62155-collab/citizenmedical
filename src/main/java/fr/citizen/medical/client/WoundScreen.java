package fr.citizen.medical.client;

import fr.citizen.medical.net.HealPayload;
import fr.citizen.medical.net.RequestWoundsPayload;
import fr.citizen.medical.net.WoundInfoPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Fiche d'examen medical (style parchemin sombre) avec le bouton "Soigner". */
public class WoundScreen extends Screen {
    private static final int W = 300, H = 190;
    private static final int GOLD = 0xFFC9A44C, PARCH = 0xFFEADBC0, MUTED = 0xFFA89880, RED = 0xFFD05050, GREEN = 0xFF9FD86B;

    private WoundInfoPayload info;
    private int refresh;

    public WoundScreen(WoundInfoPayload info) {
        super(Component.literal("Examen médical"));
        this.info = info;
    }

    public UUID target() { return info.target(); }

    public void update(WoundInfoPayload p) {
        this.info = p;
        rebuildWidgets();
    }

    @Override public boolean isPauseScreen() { return false; }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        Button heal = addRenderableWidget(Button.builder(Component.literal("§a✚ Soigner"),
                b -> PacketDistributor.sendToServer(new HealPayload(info.target()))).bounds(l + 10, t + H - 26, 140, 18).build());
        heal.active = info.wounded() && !info.treated() && info.healError().isEmpty();
        if (!info.wounded()) heal.setTooltip(Tooltip.create(Component.literal("Aucune blessure à soigner.")));
        else if (info.treated()) heal.setTooltip(Tooltip.create(Component.literal("Déjà soigné.")));
        else if (!info.healError().isEmpty()) heal.setTooltip(Tooltip.create(Component.literal(info.healError())));
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(l + W - 110, t + H - 26, 100, 18).build());
    }

    @Override
    public void tick() {
        // mise a jour automatique toutes les 5 s (compteurs, distance)
        if (++refresh % 100 == 0) PacketDistributor.sendToServer(new RequestWoundsPayload(info.target()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // fond + cadre (renderBackground) puis boutons
        int l = left(), t = top();
        g.drawString(font, "✚ Examen médical — " + info.name(), l + 8, t + 5, GOLD, false);

        int y = t + 26;
        float hearts = info.health() / 2f;
        g.drawString(font, String.format(java.util.Locale.ROOT, "Santé : %.1f / %d ❤", hearts, (int) (info.maxHealth() / 2f)), l + 10, y, PARCH, false);
        y += 14;
        if (!info.wounded()) {
            g.drawString(font, "Aucune blessure.", l + 10, y, GREEN, false);
        } else {
            for (String line : info.lines()) {
                int color = line.startsWith("Blessure") ? RED : line.startsWith("Saignement : arrêté") ? GREEN : PARCH;
                for (FormattedCharSequence part : font.split(Component.literal(line), W - 20)) {
                    if (y > t + H - 34) break;
                    g.drawString(font, part, l + 10, y, color, false);
                    y += 11;
                }
                y += 2;
            }
        }
        if (info.wounded() && !info.treated() && !info.healError().isEmpty()) {
            g.drawString(font, info.healError().length() > 52 ? info.healError().substring(0, 52) + "…" : info.healError(), l + 10, t + H - 38, MUTED, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top();
        g.fill(0, 0, width, height, 0x60000000);
        g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFFB8963E);
        g.fill(l, t, l + W, t + H, 0xF0140E0A);
        g.fill(l, t + 18, l + W, t + 19, 0xFF6B5428);
    }
}
