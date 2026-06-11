package Polfg.Polfg;

enum Rarity {
    COMMON("§aОбычный"),
    RARE("§9Редкий"),
    EPIC("§5Эпический"),
    LEGENDARY("§6§lЛегендарный"),
    MYTHICAL("§d§l✦ Мифический ✦");
    final String displayName;
    Rarity(String displayName) {
        this.displayName = displayName;
    }
    public String getDisplay() {
        return displayName;
    }
}
