package fr.citizen.medical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Liste des lits d'hopital poses (pour y transporter les joueurs a terre). */
public final class BedRegistry extends SavedData {
    public record Spot(ServerLevel level, BlockPos pos) { }

    private record Bed(String dim, long pos) { }

    private final Set<Bed> beds = new HashSet<>();

    public static BedRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BedRegistry::new, BedRegistry::load, null), "citizenmedical_beds");
    }

    private static BedRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BedRegistry r = new BedRegistry();
        ListTag list = tag.getList("beds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            r.beds.add(new Bed(b.getString("dim"), b.getLong("pos")));
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Bed b : beds) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", b.dim());
            t.putLong("pos", b.pos());
            list.add(t);
        }
        tag.put("beds", list);
        return tag;
    }

    public void add(ServerLevel level, BlockPos pos) {
        if (beds.add(new Bed(level.dimension().location().toString(), pos.asLong()))) setDirty();
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (beds.remove(new Bed(level.dimension().location().toString(), pos.asLong()))) setDirty();
    }

    public int size() { return beds.size(); }

    /** Lit le plus proche (meme dimension d'abord, sinon n'importe quel lit). Les lits disparus sont oublies. */
    public Spot nearest(MinecraftServer server, ServerPlayer p) {
        Spot best = null, other = null;
        double bestD = Double.MAX_VALUE;
        Iterator<Bed> it = beds.iterator();
        while (it.hasNext()) {
            Bed b = it.next();
            ResourceLocation dim = ResourceLocation.tryParse(b.dim());
            ServerLevel lvl = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (lvl == null) continue;
            BlockPos pos = BlockPos.of(b.pos());
            if (!lvl.getBlockState(pos).is(CitizenMedical.HOSPITAL_BED.get())) { it.remove(); setDirty(); continue; }
            if (lvl == p.level()) {
                double d = pos.distToCenterSqr(p.position());
                if (d < bestD) { bestD = d; best = new Spot(lvl, pos); }
            } else if (other == null) {
                other = new Spot(lvl, pos);
            }
        }
        return best != null ? best : other;
    }
}
