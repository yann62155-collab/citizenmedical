package fr.citizen.medical;

import com.mojang.logging.LogUtils;
import fr.citizen.medical.net.Network;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Citizen Medical : blessures, saignement, bandages, boiterie et soins par les medecins
 * de l'entreprise Hopital (Citizen Economy).
 */
@Mod(CitizenMedical.MOD_ID)
public final class CitizenMedical {
    public static final String MOD_ID = "citizenmedical";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));

    public CitizenMedical(IEventBus modBus, ModContainer container) {
        MedicalConfig.load();
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.COMBAT || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(BANDAGE.get());
            }
        });
        Network.register(modBus);
        MedicalEvents.register(NeoForge.EVENT_BUS);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            fr.citizen.medical.client.ClientSetup.init(modBus);
        }
    }
}
