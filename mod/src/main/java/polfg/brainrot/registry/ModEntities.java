package polfg.brainrot.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import polfg.brainrot.Brainrot;
import polfg.brainrot.entity.BrainrotMob;

/**
 * Реестр кастомных энтити.
 *
 * Пока здесь один тестовый брейнрот — он нужен, чтобы проверить в CI всю цепочку
 * "реестр → атрибуты → клиентский рендерер". Когда сборка станет зелёной, сюда
 * переедет весь enum MobType из brainrot-bases (68 видов с ценами и редкостями),
 * но уже не ванильными коровами и курицами, а своими типами.
 */
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Brainrot.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BrainrotMob>> BRAINROT =
            ENTITY_TYPES.register("brainrot", id -> EntityType.Builder
                    .of(BrainrotMob::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.2F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    // build(ResourceKey) — проверено первым билдом на 1.21.4, вариант со строкой
    // в этой версии уже не подходит.
}
