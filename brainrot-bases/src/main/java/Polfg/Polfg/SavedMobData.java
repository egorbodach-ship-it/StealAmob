package Polfg.Polfg;

class SavedMobData {
    final String base;
    final String mobPoint;
    final String collectorPoint;
    final MobType mobType;
    final long luckyBlockRemainingMs;
    final boolean luckyBlockReady;
    final String mutation;
    final boolean snowy;
    SavedMobData(String base, String mobPoint, String collectorPoint, MobType mobType) {
        this(base, mobPoint, collectorPoint, mobType, -1L, false, "NONE", false);
    }
    SavedMobData(String base, String mobPoint, String collectorPoint, MobType mobType, long luckyBlockRemainingMs) {
        this(base, mobPoint, collectorPoint, mobType, luckyBlockRemainingMs, false, "NONE", false);
    }
    SavedMobData(String base, String mobPoint, String collectorPoint, MobType mobType, long luckyBlockRemainingMs, boolean luckyBlockReady) {
        this(base, mobPoint, collectorPoint, mobType, luckyBlockRemainingMs, luckyBlockReady, "NONE", false);
    }
    SavedMobData(String base, String mobPoint, String collectorPoint, MobType mobType,
                 long luckyBlockRemainingMs, boolean luckyBlockReady,
                 String mutation, boolean snowy) {
        this.base = base;
        this.mobPoint = mobPoint;
        this.collectorPoint = collectorPoint;
        this.mobType = mobType;
        this.luckyBlockRemainingMs = luckyBlockRemainingMs;
        this.luckyBlockReady = luckyBlockReady;
        this.mutation = (mutation != null) ? mutation : "NONE";
        this.snowy = snowy;
    }
}
