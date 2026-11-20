package fr.citizen.medical;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /medical               : votre etat
 * /medical examiner <j>  : medecin (ou OP)
 * /medical soigner <j>   : medecin a cote du joueur (OP : n'importe ou)
 * /medical blesser <j> [cause], /medical guerir <j>, /medical recharger : OP
 */
public final class MedicalCommands {
    private MedicalCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("medical")
                .executes(MedicalCommands::self)
                .then(Commands.literal("aide").executes(MedicalCommands::help))
                .then(Commands.literal("examiner").then(Commands.argument("joueur", EntityArgument.player()).executes(MedicalCommands::examine)))
                .then(Commands.literal("soigner").then(Commands.argument("joueur", EntityArgument.player()).executes(MedicalCommands::treat)))
                .then(Commands.literal("blesser").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(c -> wound(c, "Blessure"))
                                .then(Commands.argument("cause", StringArgumentType.greedyString())
                                        .executes(c -> wound(c, StringArgumentType.getString(c, "cause"))))))
                .then(Commands.literal("guerir").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("joueur", EntityArgument.player()).executes(MedicalCommands::cure)))
                .then(Commands.literal("recharger").requires(s -> s.hasPermission(2)).executes(c -> {
                    MedicalConfig.load();
                    Hospital.clearAll();
                    c.getSource().sendSuccess(() -> Component.literal("Citizen Médical : configuration rechargée."), true);
                    return 1;
                })));
    }

    private static int self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Wounds.State s = Wounds.get(p);
        p.sendSystemMessage(Component.literal("✚ Votre état de santé").withStyle(ChatFormatting.GOLD));
        for (String l : MedicalEvents.describe(s)) p.sendSystemMessage(Component.literal("   " + l).withStyle(s.wounded() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        if (Hospital.isDoctor(p)) p.sendSystemMessage(Component.literal("   Vous êtes médecin : Alt + clic droit sur un joueur pour l'examiner.").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        String[] lines = {
                "✚ Citizen Médical",
                "/medical — votre état",
                "/medical examiner <joueur> — (médecin) examiner un joueur",
                "/medical soigner <joueur> — (médecin) soigner un joueur à côté de vous",
                "Bandage : clic droit pour se bander, ou clic droit sur un joueur blessé",
                "Médecin : touche Alt puis clic droit sur un joueur (ou accroupi + clic droit main vide)",
                "OP : /medical blesser <joueur> [cause] · /medical guerir <joueur> · /medical recharger"};
        for (String l : lines) s.sendSystemMessage(Component.literal(l).withStyle(l.startsWith("✚") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return 1;
    }

    private static int examine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer doc = c.getSource().getPlayerOrException();
        ServerPlayer t = EntityArgument.getPlayer(c, "joueur");
        if (!Hospital.isDoctor(doc)) return notDoctor(doc);
        if (doc.level() != t.level() || doc.distanceTo(t) > MedicalConfig.get().distanceExamen) {
            doc.sendSystemMessage(Component.literal("Ce patient est trop loin pour être examiné.").withStyle(ChatFormatting.RED));
            return 0;
        }
        MedicalEvents.examineInChat(doc, t);
        return 1;
    }

    private static int treat(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer doc = c.getSource().getPlayerOrException();
        ServerPlayer t = EntityArgument.getPlayer(c, "joueur");
        String err = canTreat(doc, t);
        if (err != null) {
            doc.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.RED));
            return 0;
        }
        return Wounds.treat(t, doc) ? 1 : 0;
    }

    /** null si le soin est permis, sinon le message d'erreur. Partage avec le bouton du HUD. */
    public static String canTreat(ServerPlayer doc, ServerPlayer t) {
        if (!Hospital.isDoctor(doc)) {
            return Hospital.known(doc.getUUID()) ? "Seuls les médecins embauchés à l'hôpital (emblème HOP) peuvent soigner." : "Vérification de votre métier en cours… réessayez dans un instant.";
        }
        if (doc == t) return "Un médecin ne peut pas se soigner lui-même : faites appel à un collègue.";
        if ((doc.level() != t.level() || doc.distanceTo(t) > MedicalConfig.get().distanceSoin)) {
            return "Approchez-vous du patient (moins de " + (int) MedicalConfig.get().distanceSoin + " blocs) pour le soigner.";
        }
        return null;
    }

    private static int notDoctor(ServerPlayer p) {
        p.sendSystemMessage(Component.literal(Hospital.known(p.getUUID()) ? "Seuls les médecins de l'hôpital peuvent examiner les blessures."
                : "Vérification de votre métier en cours… réessayez dans un instant.").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int wound(CommandContext<CommandSourceStack> c, String cause) throws CommandSyntaxException {
        ServerPlayer t = EntityArgument.getPlayer(c, "joueur");
        if (Wounds.wound(t, cause)) {
            c.getSource().sendSuccess(() -> Component.literal(t.getGameProfile().getName() + " est blessé : " + cause), true);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(t.getGameProfile().getName() + " saigne déjà."));
        return 0;
    }

    private static int cure(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer t = EntityArgument.getPlayer(c, "joueur");
        Wounds.clear(t);
        t.sendSystemMessage(Component.literal("✚ Vos blessures ont été entièrement guéries.").withStyle(ChatFormatting.GREEN));
        c.getSource().sendSuccess(() -> Component.literal(t.getGameProfile().getName() + " est entièrement guéri."), true);
        return 1;
    }
}
