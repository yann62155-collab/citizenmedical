package fr.citizen.medical;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Est-ce un medecin ?" = patron, gerant ou employe d'une entreprise ACTIVE de type hopital dans Citizen Economy.
 * Lecture par reflexion (comme Citizen Metiers) : aucune dependance de compilation, jamais bloquant pour le serveur.
 */
public final class Hospital {
    private record Entry(boolean doctor, String hospital, long at) { }

    private static final long MAX_AGE_MS = 20_000;
    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static volatile boolean warned;

    private Hospital() { }

    /** Valeur en cache (relance une lecture en arriere-plan si elle est vieille). */
    public static boolean isDoctor(ServerPlayer p) {
        if (MedicalConfig.get().opsSontMedecins && p.hasPermissions(2)) return true;
        Entry e = CACHE.get(p.getUUID());
        if (e == null || System.currentTimeMillis() - e.at > MAX_AGE_MS) refresh(p.getUUID());
        return e != null && e.doctor;
    }

    /** Nom de l'hopital du medecin (pour les messages), ou "" . */
    public static String hospitalOf(ServerPlayer p) {
        Entry e = CACHE.get(p.getUUID());
        return e == null || e.hospital == null ? "" : e.hospital;
    }

    public static boolean known(UUID id) { return CACHE.containsKey(id); }

    public static void forget(UUID id) { CACHE.remove(id); }

    public static void clearAll() { CACHE.clear(); }

    public static CompletableFuture<Boolean> refresh(UUID player) {
        if (!LOADING.add(player)) return CompletableFuture.completedFuture(CACHE.containsKey(player) && CACHE.get(player).doctor);
        return load(player).whenComplete((r, t) -> LOADING.remove(player));
    }

    private static CompletableFuture<Boolean> load(UUID player) {
        try {
            Object companies = companyManager();
            if (companies == null) {
                CACHE.put(player, new Entry(false, "", System.currentTimeMillis()));
                return CompletableFuture.completedFuture(false);
            }
            Method m = companies.getClass().getMethod("companiesOf", UUID.class);
            CompletableFuture<?> f = (CompletableFuture<?>) m.invoke(companies, player);
            return f.thenApply(list -> {
                String found = null;
                for (Object c : (List<?>) list) {
                    String status = String.valueOf(call(c, "status"));
                    if (!"ACTIVE".equals(status)) continue;
                    if (isHospital(str(call(c, "matricule")), str(call(c, "name")), str(call(c, "sector")), str(call(c, "emblem")))) {
                        found = str(call(c, "name"));
                        break;
                    }
                }
                CACHE.put(player, new Entry(found != null, found == null ? "" : found, System.currentTimeMillis()));
                return found != null;
            }).exceptionally(t -> {
                CitizenMedical.LOGGER.warn("Citizen Medical : lecture des entreprises impossible pour {}", player, t);
                return false;
            });
        } catch (Throwable t) {
            if (!warned) { warned = true; CitizenMedical.LOGGER.warn("Citizen Medical : Citizen Economy injoignable", t); }
            return CompletableFuture.completedFuture(false);
        }
    }

    public static boolean isHospital(String matricule, String name, String sector, String emblem) {
        MedicalConfig.Data d = MedicalConfig.get();
        String mat = norm(matricule), emb = norm(emblem);
        for (String s : d.matriculesHopital) if (!norm(s).isEmpty() && norm(s).equals(mat)) return true;
        for (String s : d.emblemesHopital) if (!norm(s).isEmpty() && norm(s).equals(emb)) return true;
        String n = norm(name), sec = norm(sector);
        for (String k : d.motsClesHopital) {
            String kk = norm(k);
            if (kk.isEmpty()) continue;
            if (n.contains(kk) || sec.contains(kk)) return true;
        }
        return false;
    }

    static String norm(String s) {
        if (s == null || s.equals("null")) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static Object call(Object o, String method) {
        try { return o.getClass().getMethod(method).invoke(o); } catch (Exception e) { return null; }
    }

    private static Object companyManager() throws Exception {
        if (!ModList.get().isLoaded("citizeneconomy")) return null;
        Class<?> rt;
        try { rt = Class.forName("fr.citizen.economy.platform.EconomyRuntime"); }
        catch (ClassNotFoundException e) { return null; }
        Object cur = rt.getMethod("current").invoke(null);
        if (cur == null) return null;
        Object eco = rt.getMethod("economy").invoke(cur);
        return eco.getClass().getField("companies").get(eco);
    }
}
