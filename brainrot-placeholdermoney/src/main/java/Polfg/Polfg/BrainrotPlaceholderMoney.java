
package Polfg.Polfg;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class BrainrotPlaceholderMoney extends JavaPlugin {

    @Override
    public void onEnable() {
        // Проверяем наличие PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MoneyExpansion().register();
            getLogger().info("Brainrot placeholders registered!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Disabling...");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    // Внутренний класс для работы с PAPI
    public class MoneyExpansion extends PlaceholderExpansion {

        private Economy economy;

        public MoneyExpansion() {
            setupEconomy();
        }

        private boolean setupEconomy() {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            economy = rsp.getProvider();
            return economy != null;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "brainrotmoney";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Polfg";
        }

        @Override
        public @NotNull String getVersion() {
            return "1.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) {
                return "";
            }

            if (economy == null) {
                setupEconomy();
                if (economy == null) return "0";
            }

            double balance = economy.getBalance(player);

            switch (identifier.toLowerCase()) {
                case "balance_short":
                    return formatMoneyShort(balance);
                case "balance_short_dollar":
                    return formatMoneyShort(balance) + "$";
                case "balance_short_dollar_prefix":
                    return "$" + formatMoneyShort(balance);
                case "balance_detailed":
                    return formatMoneyDetailed(balance);
                case "balance_ru":
                    return formatMoneyRu(balance);
                case "balance_ru_rub":
                    return formatMoneyRu(balance) + "₽";
                case "balance_full":
                    return formatMoneyFull(balance);
                case "balance_raw":
                    return String.valueOf((long) balance);
                case "balance_decimal":
                    return String.format("%.1f", balance);
                case "balance_colored":
                    if (balance >= 1000000) return "§a" + formatMoneyShort(balance) + "$";
                    else if (balance >= 10000) return "§e" + formatMoneyShort(balance) + "$";
                    else if (balance > 0) return "§f" + formatMoneyShort(balance) + "$";
                    else return "§c" + formatMoneyShort(balance) + "$";
                case "balance_tab":
                    return formatMoneyTab(balance);
                case "balance_scoreboard":
                    return formatMoneyScoreboard(balance);
                default:
                    return null;
            }
        }

        private String formatMoneyShort(double amount) {
            if (amount < 0) return "-" + formatMoneyShort(-amount);
            if (amount < 1000) return String.valueOf((int) amount);
            if (amount < 10000) {
                double value = amount / 1000;
                return (value == (int) value) ? (int) value + "K" : String.format("%.1fK", value).replace(".0K", "K").replace(",", ".");
            }
            if (amount < 1000000) return (int) (amount / 1000) + "K";
            if (amount < 10000000) {
                double value = amount / 1000000;
                return (value == (int) value) ? (int) value + "M" : String.format("%.1fM", value).replace(".0M", "M").replace(",", ".");
            }
            if (amount < 1000000000) return (int) (amount / 1000000) + "M";
            if (amount < 10000000000L) {
                double value = amount / 1000000000;
                return (value == (int) value) ? (int) value + "B" : String.format("%.1fB", value).replace(".0B", "B").replace(",", ".");
            }
            return (int) (amount / 1000000000) + "B";
        }

        private String formatMoneyDetailed(double amount) {
            if (amount < 0) return "-" + formatMoneyDetailed(-amount);
            if (amount < 1000) return (int) amount + "$";
            double divisor = amount < 1000000 ? 1000 : (amount < 1000000000 ? 1000000 : 1000000000);
            String suffix = amount < 1000000 ? "K$" : (amount < 1000000000 ? "M$" : "B$");
            double value = amount / divisor;
            return String.format("%.2f", value).replace(",", ".").replaceAll("\\.?0+$", "") + suffix;
        }

        private String formatMoneyRu(double amount) {
            if (amount < 0) return "-" + formatMoneyRu(-amount);
            if (amount < 1000) return String.valueOf((int) amount);
            if (amount < 1000000) return formatRuUnit(amount, 1000, "К");
            if (amount < 1000000000) return formatRuUnit(amount, 1000000, "М");
            return formatRuUnit(amount, 1000000000, "Млрд");
        }

        private String formatRuUnit(double amount, double div, String unit) {
            double val = amount / div;
            return String.format("%.1f", val).replace(",", ".").replace(".0", "") + unit;
        }

        private String formatMoneyFull(double amount) {
            java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
            return df.format((long) amount) + "$";
        }

        private String formatMoneyTab(double amount) {
            if (amount < 0) return "§c-" + formatMoneyShort(-amount);
            String formatted = formatMoneyShort(amount);
            if (amount >= 10000000) return "§6" + formatted;
            if (amount >= 1000000) return "§a" + formatted;
            if (amount >= 100000) return "§e" + formatted;
            if (amount >= 10000) return "§f" + formatted;
            return "§7" + formatted;
        }

        private String formatMoneyScoreboard(double amount) {
            String formatted = formatMoneyShort(amount);
            while (formatted.length() < 6) formatted = " " + formatted;
            return "§a$" + formatted;
        }
    }
}
