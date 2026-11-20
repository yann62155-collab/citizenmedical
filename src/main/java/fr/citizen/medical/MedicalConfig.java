package fr.citizen.medical;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reglages modifiables dans config/citizenmedical.json (/medical recharger pour les relire). */
public final class MedicalConfig {
    public static final class Data {
        /** Chance (0 a 1) qu'un coup qualifiant provoque une blessure. */
        public double chanceBlessure = 0.5;
        /** Degats minimum (en points de vie, 2 = 1 coeur) d'un coup (joueur, monstre, projectile, explosion). */
        public double degatsMinCoup = 4.0;
        /** Degats minimum d'une chute. */
        public double degatsMinChute = 4.0;

        /** Sans bandage : perte de vie (2 = 1 coeur) toutes les X minutes. */
        public double perteSansBandage = 2.0;
        public int minutesSansBandage = 5;
        /** Avec bandage : perte de vie (1 = 1/2 coeur) toutes les X minutes. */
        public double perteAvecBandage = 1.0;
        public int minutesAvecBandage = 10;
        /** false = le saignement s'arrete a 1/2 coeur et ne tue jamais. */
        public boolean saignementPeutTuer = true;

        /** La boiterie dure au moins X minutes apres l'apparition de la blessure, et jusqu'au soin d'un medecin. */
        public int minutesBoiterieMin = 10;
        /** Ralentissement pendant la boiterie (0.35 = -35 % de vitesse). */
        public double ralentissementBoiterie = 0.35;

        /** Distance max (blocs) pour examiner / soigner. */
        public double distanceExamen = 32.0;
        public double distanceSoin = 6.0;

        /** Une entreprise ACTIVE est un hopital si son emblème, son matricule, son nom ou son secteur correspond. */
        public List<String> emblemesHopital = new ArrayList<>(List.of("HOP", "MED"));
        public List<String> matriculesHopital = new ArrayList<>();
        public List<String> motsClesHopital = new ArrayList<>(List.of("hopital", "hospital", "medecin", "medical", "clinique", "infirmerie", "guerisseur", "dispensaire"));
        /** Les operateurs (OP niveau 2) ont aussi les droits de medecin. */
        public boolean opsSontMedecins = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile Data data = new Data();

    public static Data get() { return data; }

    public static Path file() { return FMLPaths.CONFIGDIR.get().resolve("citizenmedical.json"); }

    public static synchronized void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                }
            }
            save();
        } catch (Exception e) {
            CitizenMedical.LOGGER.warn("Citizen Medical : config illisible, valeurs par defaut utilisees", e);
            data = new Data();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { GSON.toJson(data, w); }
        } catch (Exception e) {
            CitizenMedical.LOGGER.warn("Citizen Medical : impossible d'ecrire la config", e);
        }
    }

    private MedicalConfig() { }
}
