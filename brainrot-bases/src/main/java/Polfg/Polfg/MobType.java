package Polfg.Polfg;

import org.bukkit.Material;
import org.bukkit.entity.*;

public enum MobType {
    CHICKEN("Курица", EntityType.CHICKEN, 3.0, Material.CHICKEN_SPAWN_EGG, 250, false, Rarity.COMMON),
    COW("Корова", EntityType.COW, 5.0, Material.COW_SPAWN_EGG, 500, false, Rarity.COMMON),
    SHEEP("Овца", EntityType.SHEEP, 7.0, Material.SHEEP_SPAWN_EGG, 750, false, Rarity.COMMON),
    PIG("Свинья", EntityType.PIG, 9.0, Material.PIG_SPAWN_EGG, 1000, false, Rarity.COMMON),
    RABBIT("Кролик", EntityType.RABBIT, 10.0, Material.RABBIT_SPAWN_EGG, 1200, false, Rarity.COMMON),
    PARROT("Попугай", EntityType.PARROT, 13.0, Material.PARROT_SPAWN_EGG, 1500, false, Rarity.COMMON),
    TURTLE("Черепаха", EntityType.TURTLE, 15.0, Material.TURTLE_SPAWN_EGG, 1800, false, Rarity.COMMON),
    FOX("Лиса", EntityType.FOX, 15.0, Material.FOX_SPAWN_EGG, 2000, true, Rarity.RARE),
    PANDA("Панда", EntityType.PANDA, 30.0, Material.PANDA_SPAWN_EGG, 4000, true, Rarity.RARE),
    WOLF("Бульбульдог", EntityType.WOLF, 35.0, Material.WOLF_SPAWN_EGG, 4500, true, Rarity.RARE),
    DOLPHIN("Дельфин", EntityType.DOLPHIN, 40.0, Material.DOLPHIN_SPAWN_EGG, 5000, true, Rarity.RARE),
    HORSE("Лошадь", EntityType.HORSE, 50.0, Material.HORSE_SPAWN_EGG, 6500, true, Rarity.RARE),
    LLAMA("Лама", EntityType.LLAMA, 55.0, Material.LLAMA_SPAWN_EGG, 7500, true, Rarity.RARE),
    POLAR_BEAR("Белый медведь", EntityType.POLAR_BEAR, 65.0, Material.POLAR_BEAR_SPAWN_EGG, 9000, true, Rarity.RARE),
    RAVAGER("Разоритель", EntityType.RAVAGER, 67.0, Material.RAVAGER_SPAWN_EGG, 9200, true, Rarity.RARE),
    CAT_KUZYA("Кот Кузя", EntityType.CAT, 45.0, Material.CAT_SPAWN_EGG, 5500, true, Rarity.RARE),
    ENDERMAN("Эндермен", EntityType.ENDERMAN, 75.0, Material.ENDERMAN_SPAWN_EGG, 10000, true, Rarity.EPIC),
    BLAZE("Ифрит", EntityType.BLAZE, 90.0, Material.BLAZE_SPAWN_EGG, 12500, true, Rarity.EPIC),
    WITHER_SKELETON("Визер-скелет", EntityType.WITHER_SKELETON, 100.0, Material.WITHER_SKELETON_SPAWN_EGG, 15000, true, Rarity.EPIC),
    IRON_GOLEM("Железный голем", EntityType.IRON_GOLEM, 115.0, Material.IRON_BLOCK, 17500, true, Rarity.EPIC),
    GUARDIAN("Страж", EntityType.GUARDIAN, 135.0, Material.GUARDIAN_SPAWN_EGG, 22500, true, Rarity.EPIC),
    ENDERMITE("Эндермит", EntityType.ENDERMITE, 140.0, Material.ENDERMITE_SPAWN_EGG, 23500, true, Rarity.EPIC),
    EVOKER("Заклинатель", EntityType.EVOKER, 150.0, Material.EVOKER_SPAWN_EGG, 25000, true, Rarity.EPIC),
    VINDICATOR("Поборник", EntityType.VINDICATOR, 160.0, Material.VINDICATOR_SPAWN_EGG, 27500, true, Rarity.EPIC),
    HUSK("Кадавр", EntityType.HUSK, 175.0, Material.HUSK_SPAWN_EGG, 30000, true, Rarity.EPIC),
    STRAY("Зимогор", EntityType.STRAY, 225.0, Material.STRAY_SPAWN_EGG, 35000, true, Rarity.EPIC),
    ELDER_GUARDIAN("Древний страж", EntityType.ELDER_GUARDIAN, 250.0, Material.ELDER_GUARDIAN_SPAWN_EGG, 37500, true, Rarity.EPIC),
    MOOSHROOM("Грибная корова", EntityType.MOOSHROOM, 200.0, Material.RED_MUSHROOM_BLOCK, 35000, true, Rarity.LEGENDARY),
    ZOMBIE_HORSE("Зомби-лошадь", EntityType.ZOMBIE_HORSE, 300.0, Material.ZOMBIE_HORSE_SPAWN_EGG, 50000, true, Rarity.LEGENDARY),
    SKELETON_HORSE("Скелет-лошадь", EntityType.SKELETON_HORSE, 450.0, Material.SKELETON_HORSE_SPAWN_EGG, 75000, true, Rarity.LEGENDARY),
    STRIDER("Страйдер", EntityType.STRIDER, 500.0, Material.STRIDER_SPAWN_EGG, 100000, true, Rarity.LEGENDARY),
    PHANTOM("Фантом", EntityType.PHANTOM, 600.0, Material.PHANTOM_SPAWN_EGG, 150000, true, Rarity.LEGENDARY),
    VEX("Векс", EntityType.VEX, 750.0, Material.VEX_SPAWN_EGG, 200000, true, Rarity.LEGENDARY),
    MAGMA_CUBE("Магмовый куб", EntityType.MAGMA_CUBE, 1000.0, Material.MAGMA_CUBE_SPAWN_EGG, 250000, true, Rarity.LEGENDARY),
    HOGLIN("Хоглин", EntityType.HOGLIN, 1000.0, Material.HOGLIN_SPAWN_EGG, 255000, true, Rarity.LEGENDARY),
    PIGLIN_BRUTE("Пиглин-воин", EntityType.PIGLIN_BRUTE, 1100.0, Material.PIGLIN_BRUTE_SPAWN_EGG, 256000, true, Rarity.LEGENDARY),
    PILLAGER("Разбойник", EntityType.PILLAGER, 1100.0, Material.PILLAGER_SPAWN_EGG, 275000, true, Rarity.LEGENDARY),
    SILVERFISH("Чешуйница", EntityType.SILVERFISH, 1200.0, Material.SILVERFISH_SPAWN_EGG, 285000, true, Rarity.LEGENDARY),
    WITCH("Ведьма", EntityType.WITCH, 1200.0, Material.WITCH_SPAWN_EGG, 300000, true, Rarity.LEGENDARY),
    CAVE_SPIDER("Пещерный паук", EntityType.CAVE_SPIDER, 1200.0, Material.CAVE_SPIDER_SPAWN_EGG, 310000, true, Rarity.LEGENDARY),
    SPIDER("Паук", EntityType.SPIDER, 1200.0, Material.SPIDER_SPAWN_EGG, 315000, true, Rarity.LEGENDARY),
    ILLUSIONER("Иллюзионист", EntityType.ILLUSIONER, 1300.0, Material.SPECTRAL_ARROW, 320000, true, Rarity.LEGENDARY),
    OCELOT("Оцелот", EntityType.OCELOT, 1300.0, Material.OCELOT_SPAWN_EGG, 325000, true, Rarity.LEGENDARY),
    BAT("Летучая мышь", EntityType.BAT, 1800.0, Material.BAT_SPAWN_EGG, 325000, true, Rarity.LEGENDARY),
    BEE("Пчела", EntityType.BEE, 1400.0, Material.BEE_SPAWN_EGG, 345000, true, Rarity.LEGENDARY),
    CAMEL("Верблюд", EntityType.CAMEL, 1200.0, Material.CAMEL_SPAWN_EGG, 300000, true, Rarity.MYTHICAL),
    BROWN_PANDA("Коричневая панда", EntityType.PANDA, 1700.0, Material.PANDA_SPAWN_EGG, 400000, true, Rarity.MYTHICAL),
    CREEPER("Крипер", EntityType.CREEPER, 2100.0, Material.CREEPER_SPAWN_EGG, 450000, true, Rarity.MYTHICAL),
    DROWNED("Утопленник", EntityType.DROWNED, 2500.0, Material.DROWNED_SPAWN_EGG, 500000, true, Rarity.MYTHICAL),
    FROG("Лягушка", EntityType.FROG, 3000.0, Material.FROG_SPAWN_EGG, 600000, true, Rarity.MYTHICAL),
    SPONGE("Лаки-Блок", EntityType.ITEM_DISPLAY, 0.0, Material.SPONGE, 750000, true, Rarity.MYTHICAL),
    GOAT("Козёл", EntityType.GOAT, 5000.0, Material.GOAT_SPAWN_EGG, 1000000, true, Rarity.MYTHICAL),
    GLOW_SQUID("Светящийся кальмар", EntityType.GLOW_SQUID, 6000.0, Material.GLOW_SQUID_SPAWN_EGG, 1000000, true, Rarity.MYTHICAL),
    WANDERING_TRADER("Странствующий торговец", EntityType.WANDERING_TRADER, 7500.0, Material.WANDERING_TRADER_SPAWN_EGG, 1200000, true, Rarity.MYTHICAL),
    SNOW_GOLEM("Снеговик", EntityType.SNOW_GOLEM, 7500.0, Material.CARVED_PUMPKIN, 1500000, true, Rarity.MYTHICAL),
    PIGLIN("Пиглин", EntityType.PIGLIN, 8000.0, Material.PIGLIN_SPAWN_EGG, 1800000, true, Rarity.MYTHICAL),
    MYTHIC_SKELETON_HORSE("Скелет-лошадь", EntityType.SKELETON_HORSE, 8500.0, Material.SKELETON_HORSE_SPAWN_EGG, 2000000, true, Rarity.MYTHICAL),
    ZOMBIFIED_PIGLIN("Зомби-пиглин", EntityType.ZOMBIFIED_PIGLIN, 9000.0, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, 2200000, true, Rarity.MYTHICAL),
    ALLAY("Аллей", EntityType.ALLAY, 9500.0, Material.ALLAY_SPAWN_EGG, 2500000, true, Rarity.MYTHICAL),
    SNIFFER("Сниффер", EntityType.SNIFFER, 10000.0, Material.SNIFFER_EGG, 2800000, true, Rarity.MYTHICAL),
    ZOGLIN("Зоглин", EntityType.ZOGLIN, 11000.0, Material.ZOGLIN_SPAWN_EGG, 3000000, true, Rarity.MYTHICAL),
    AXOLOTL("Аксолотль", EntityType.AXOLOTL, 12000.0, Material.AXOLOTL_SPAWN_EGG, 3500000, true, Rarity.MYTHICAL),
    WARDEN("Варден", EntityType.WARDEN, 15000.0, Material.SCULK_SHRIEKER, 5000000, true, Rarity.MYTHICAL),
    ROT_WALKER("Гнилоход", EntityType.ITEM_DISPLAY, 50000.0, Material.SLIME_BALL, 9999999, true, Rarity.MYTHICAL);
    public final String name;
    public final EntityType type;
    public double baseIncome;
    public final Material icon;
    public int sellPrice;
    public final boolean isRare;
    public final Rarity rarity;
    MobType(String name, EntityType type, double baseIncome, Material icon, int sellPrice, boolean isRare, Rarity rarity) {
        this.name = name;
        this.type = type;
        this.baseIncome = baseIncome;
        this.icon = icon;
        this.sellPrice = sellPrice;
        this.isRare = isRare;
        this.rarity = rarity;
    }
    public String getRarityDisplay() {
        return rarity.getDisplay();
    }
    public boolean isEpic() {
        return rarity == Rarity.EPIC;
    }
    public boolean isLegendary() {
        return rarity == Rarity.LEGENDARY;
    }
    public boolean isMythical() {
        return rarity == Rarity.MYTHICAL;
    }
    public boolean isLuckyBlock() {
        return this == SPONGE;
    }
    public boolean isRotWalker() {
        return this == ROT_WALKER;
    }
    public boolean isAJMob() {
        return this == SPONGE || this == ROT_WALKER;
    }
    static MobType fromEntity(Entity entity) {
        if (entity == null) return CHICKEN;
        for (MobType mt : values()) {
            if (entity.getScoreboardTags().contains("MOB_" + mt.name())) {
                return mt;
            }
        }
        if (entity.getScoreboardTags().contains("LUCKY_BLOCK") ||
            entity.getScoreboardTags().contains("aj.luckyblock.root")) {
            return SPONGE;
        }
        if (entity.getScoreboardTags().contains("ROT_WALKER_BASE") ||
            entity.getScoreboardTags().contains("aj.rotwalker.root")) {
            return ROT_WALKER;
        }
        if (entity.getScoreboardTags().contains("MOB_RARITY_MYTHICAL")) {
            for (MobType mt : values()) {
                if (mt.isMythical() && entity.getType() == mt.type) {
                    return mt;
                }
            }
        }
        for (MobType mt : values()) {
            if (entity.getType() == mt.type) {
                if (mt == CAT_KUZYA && entity instanceof Cat cat) {
                    if (cat.getCatType() == Cat.Type.RED) {
                        return mt;
                    }
                    continue;
                }
                if (mt == BROWN_PANDA && entity instanceof Panda panda) {
                    if (panda.getMainGene() == Panda.Gene.BROWN) {
                        return mt;
                    }
                    continue;
                }
                if (mt == PANDA && entity instanceof Panda panda) {
                    if (panda.getMainGene() != Panda.Gene.BROWN) {
                        return mt;
                    }
                    continue;
                }
                if (mt == FOX && entity instanceof Fox fox) {
                    if (fox.getFoxType() == Fox.Type.RED) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MOOSHROOM && entity instanceof MushroomCow) {
                    if (entity.getScoreboardTags().contains("LEGENDARY_MOB") ||
                        entity.getScoreboardTags().contains("MOB_RARITY_LEGENDARY")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == MYTHIC_SKELETON_HORSE && entity.getType() == EntityType.SKELETON_HORSE) {
                    if (entity.getScoreboardTags().contains("MOB_RARITY_MYTHICAL")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == SKELETON_HORSE && entity.getType() == EntityType.SKELETON_HORSE) {
                    if (!entity.getScoreboardTags().contains("MOB_RARITY_MYTHICAL")) {
                        return mt;
                    }
                    continue;
                }
                if (mt == WARDEN && entity.getType() == EntityType.WARDEN) {
                    return mt;
                }
                return mt;
            }
        }
        return CHICKEN;
    }
    static MobType fromTag(Entity entity) {
        if (entity == null) return CHICKEN;
        for (MobType mt : values()) {
            if (entity.getScoreboardTags().contains("MOB_" + mt.name())) return mt;
        }
        return CHICKEN;
    }
    static MobType fromName(String name) {
        if (name == null || name.isEmpty()) return CHICKEN;
        for (MobType mt : values()) {
            if (mt.name.equalsIgnoreCase(name) ||
                mt.name().equalsIgnoreCase(name)) {
                return mt;
            }
        }
        return CHICKEN;
    }
    public Material getCorrectIcon() {
        if (this == MOOSHROOM) {
            return Material.RED_MUSHROOM_BLOCK;
        }
        if (this == SPONGE) {
            return Material.SPONGE;
        }
        if (this == ROT_WALKER) {
            return Material.SLIME_BALL;
        }
        return icon;
    }
}
