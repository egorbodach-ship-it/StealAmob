package polfg.brainrot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polfg.brainrot.command.BrainrotCommand;
import polfg.brainrot.entity.BrainrotMob;
import polfg.brainrot.registry.ModEntities;

/**
 * Точка входа мода.
 *
 * Пришло на замену двенадцати классам-наследникам JavaPlugin из старых плагинов:
 * BrainrotBases, BrainrotSpawner, BrainrotShop, BrainrotAuction, BrainrotExtras,
 * BrainrotAdmin, BrainrotCodes, BrainrotEvents, BrainrotPlaceholderMoney, SpawnWorld.
 * Всё, что раньше общалось через Bukkit.getPluginManager().getPlugin(...) и рефлексию
 * (56 таких мест), теперь просто вызывает друг друга напрямую.
 */
@Mod(Brainrot.MODID)
public final class Brainrot {

    public static final String MODID = "brainrot";
    public static final Logger LOG = LoggerFactory.getLogger("Brainrot");

    public Brainrot(IEventBus modBus) {
        // Реестры слушают шину мода.
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(this::onEntityAttributes);

        // Игровые события слушают общую шину.
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOG.info("Brainrot загружается");
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BRAINROT.get(), BrainrotMob.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BrainrotCommand.register(event.getDispatcher());
    }
}
