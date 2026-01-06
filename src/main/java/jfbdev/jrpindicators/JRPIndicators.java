package jfbdev.jrpindicators;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JRPIndicators extends JavaPlugin implements CommandExecutor, TabCompleter {

    private JRPIndicatorsExpansion placeholderExpansion;
    private int daysPerMonth;
    private final Map<String, List<Integer>> seasonMonths = new HashMap<>();
    private final Map<String, String> seasonNames = new HashMap<>();
    private String lastDayPhase = "";
    private String lastWeather = "";
    private int lastMonth = -1;
    private final Map<String, List<String>> dayGreetings = new HashMap<>();
    private final Map<String, List<String>> weatherGreetings = new HashMap<>();
    private final Map<String, List<String>> seasonGreetings = new HashMap<>();
    private boolean dayGreetingEnabled;
    private boolean weatherGreetingEnabled;
    private boolean seasonGreetingEnabled;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        daysPerMonth = getConfig().getInt("calendar.months_days_count", 28);
        loadSeasons();
        loadGreetings();
        Objects.requireNonNull(getCommand("rpreload")).setExecutor(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new JRPIndicatorsExpansion(this);
            placeholderExpansion.register();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                checkDayPhaseChange();
                checkWeatherChange();
                checkSeasonChange();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
    }

    private void loadSeasons() {
        seasonMonths.clear();
        seasonNames.clear();

        seasonMonths.put("spring", List.of(3, 4, 5));
        seasonMonths.put("summer", List.of(6, 7, 8));
        seasonMonths.put("autumn", List.of(9, 10, 11));
        seasonMonths.put("winter", List.of(12, 1, 2));

        seasonNames.put("spring", "&aВесна");
        seasonNames.put("summer", "&eЛето");
        seasonNames.put("autumn", "&6Осень");
        seasonNames.put("winter", "&bЗима");

        if (!getConfig().isConfigurationSection("seasons")) {
            return;
        }

        for (String seasonKey : Objects.requireNonNull(getConfig().getConfigurationSection("seasons")).getKeys(false)) {
            List<Integer> months = getConfig().getIntegerList("seasons." + seasonKey + ".months");
            String name = getConfig().getString("seasons." + seasonKey + ".name", seasonKey);

            if (!months.isEmpty()) {
                seasonMonths.put(seasonKey.toLowerCase(), months);
                seasonNames.put(seasonKey.toLowerCase(), name);
            }
        }
    }

    private void loadGreetings() {
        dayGreetingEnabled = getConfig().getBoolean("day-greeting.enabled", false);
        weatherGreetingEnabled = getConfig().getBoolean("weather-greeting.enabled", false);
        seasonGreetingEnabled = getConfig().getBoolean("season-greeting.enabled", false);

        dayGreetings.clear();
        weatherGreetings.clear();
        seasonGreetings.clear();

        if (getConfig().isConfigurationSection("day-greeting")) {
            for (String phase : List.of("morning", "day", "evening", "night")) {
                List<String> messages = getConfig().getStringList("day-greeting." + phase);
                if (!messages.isEmpty()) {
                    dayGreetings.put(phase, messages);
                }
            }
        }

        if (getConfig().isConfigurationSection("weather-greeting")) {
            for (String weather : List.of("sun", "rain", "snow", "storm")) {
                List<String> messages = getConfig().getStringList("weather-greeting." + weather);
                if (!messages.isEmpty()) {
                    weatherGreetings.put(weather, messages);
                }
            }
        }

        if (getConfig().isConfigurationSection("season-greeting")) {
            for (String season : List.of("spring", "summer", "autumn", "winter")) {
                List<String> messages = getConfig().getStringList("season-greeting." + season);
                if (!messages.isEmpty()) {
                    seasonGreetings.put(season, messages);
                }
            }
        }
    }

    private void checkDayPhaseChange() {
        if (!dayGreetingEnabled) return;
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return;

        String currentPhase = getCurrentDayPhase(world);
        if (!currentPhase.equals(lastDayPhase) && !currentPhase.isEmpty()) {
            List<String> messages = dayGreetings.get(currentPhase);
            if (messages != null && !messages.isEmpty()) {
                String message = messages.get(random.nextInt(messages.size()));
                Bukkit.broadcastMessage(colorize(message));
            }
            lastDayPhase = currentPhase;
        }
    }

    private void checkWeatherChange() {
        if (!weatherGreetingEnabled) return;
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return;

        String currentWeather = getCurrentWeatherType(world);
        if (!currentWeather.equals(lastWeather) && !currentWeather.isEmpty()) {
            List<String> messages = weatherGreetings.get(currentWeather);
            if (messages != null && !messages.isEmpty()) {
                String message = messages.get(random.nextInt(messages.size()));
                Bukkit.broadcastMessage(colorize(message));
            }
            lastWeather = currentWeather;
        }
    }

    private void checkSeasonChange() {
        if (!seasonGreetingEnabled) return;
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return;

        int currentMonth = getGameMonth(world);
        if (currentMonth != lastMonth && lastMonth != -1) {
            String currentSeason = getSeasonKey(world);
            List<String> messages = seasonGreetings.get(currentSeason);
            if (messages != null && !messages.isEmpty()) {
                String message = messages.get(random.nextInt(messages.size()));
                Bukkit.broadcastMessage(colorize(message));
            }
        }
        lastMonth = currentMonth;
    }

    private String getCurrentDayPhase(World world) {
        long ticks = world.getTime();
        long adjusted = (ticks + 6000) % 24000;
        int hour = (int) (adjusted / 1000);

        if (hour >= 4 && hour < 12) return "morning";
        if (hour >= 12 && hour < 18) return "day";
        if (hour >= 18 && hour < 22) return "evening";
        return "night";
    }

    private String getCurrentWeatherType(World world) {
        if (!world.hasStorm()) return "sun";
        if (world.isThundering()) return "storm";

        Block block = world.getHighestBlockAt(world.getSpawnLocation());
        float temp = (float) block.getTemperature();
        return temp < 0.15f ? "snow" : "rain";
    }

    private String getSeasonKey(World world) {
        int month = getGameMonth(world);
        for (Map.Entry<String, List<Integer>> entry : seasonMonths.entrySet()) {
            if (entry.getValue().contains(month)) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rpreload")) {
            if (!sender.hasPermission("jrpindicators.admin")) {
                sender.sendMessage(colorize(getConfig().getString("messages.no-permission","")));
                return true;
            }

            reloadConfig();
            daysPerMonth = getConfig().getInt("calendar.months_days_count", 28);
            loadSeasons();
            loadGreetings();
            lastDayPhase = "";
            lastWeather = "";
            lastMonth = -1;

            sender.sendMessage(colorize(getConfig().getString("messages.reload-success", "")));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return Collections.emptyList();
    }

    public String getDayPhase(World world) {
        long ticks = world.getTime();
        long adjusted = (ticks + 6000) % 24000;
        int hour = (int) (adjusted / 1000);

        if (hour >= 4 && hour < 12) return getConfig().getString("day.morning", "");
        if (hour >= 12 && hour < 18) return getConfig().getString("day.day", "");
        if (hour >= 18 && hour < 22) return getConfig().getString("day.evening", "");
        return getConfig().getString("day.night", "");
    }

    public String getFormattedTime(World world) {
        long ticks = world.getTime();
        long adjusted = (ticks + 6000) % 24000;
        int hours = (int) (adjusted / 1000);
        int minutes = (int) ((adjusted % 1000) * 60 / 1000);

        String fmt = getConfig().getString("time-format", "");
        return fmt
                .replace("HH", String.format("%02d", hours))
                .replace("H", String.valueOf(hours))
                .replace("mm", String.format("%02d", minutes))
                .replace("m", String.valueOf(minutes));
    }

    public String getWeather(Player player) {
        World world = player.getWorld();
        if (!world.hasStorm()) {
            return getConfig().getString("weather.sun", "");
        }
        if (world.isThundering()) {
            return getConfig().getString("weather.storm", "");
        }

        float temperature = (float) player.getLocation().getBlock().getTemperature();
        if (temperature < 0.15f) {
            return getConfig().getString("weather.snow", "");
        }
        return getConfig().getString("weather.rain", "");
    }

    public int getGameDay(World world) {
        long totalDays = world.getFullTime() / 24000;
        return (int) ((totalDays % daysPerMonth) + 1);
    }

    public int getGameMonth(World world) {
        long totalDays = world.getFullTime() / 24000;
        return (int) ((totalDays / daysPerMonth) % 12 + 1);
    }

    public String getMonthName(int month) {
        return getConfig().getString("calendar.months." + month, " " + month);
    }

    public int getWeekday(World world) {
        long totalDays = world.getFullTime() / 24000;
        long rawWeekday = totalDays % 7;
        return (int) ((rawWeekday + 6) % 7 + 1);
    }

    public String getWeekdayName(int weekday) {
        return getConfig().getString("calendar.weekdays." + weekday, " " + weekday);
    }

    public String getSeasonName(World world) {
        int month = getGameMonth(world);
        for (Map.Entry<String, List<Integer>> entry : seasonMonths.entrySet()) {
            if (entry.getValue().contains(month)) {
                return seasonNames.getOrDefault(entry.getKey(), "&7Неизвестно");
            }
        }
        return "&7Неизвестно";
    }

    public String getDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        int index = Math.round(yaw / 45) % 8;
        return getConfig().getString("directions.direction_" + index, "&7Неизвестно");
    }

    public String getShortDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        int index = Math.round(yaw / 45) % 8;
        return getConfig().getString("directions.short_" + index, "?");
    }

    public static String colorize(String msg) {
        if (msg == null || msg.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private static class JRPIndicatorsExpansion extends PlaceholderExpansion {

        private final JRPIndicators plugin;

        public JRPIndicatorsExpansion(JRPIndicators plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "jrpi";
        }

        @Override
        public @NotNull String getAuthor() {
            return "jFrostyBoy";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";

            World world = player.getWorld();

            return switch (params.toLowerCase()) {
                case "day" -> String.valueOf(plugin.getGameDay(world));
                case "day_type" -> colorize(plugin.getDayPhase(world));
                case "time" -> colorize(plugin.getFormattedTime(world));
                case "weather" -> colorize(plugin.getWeather(player));
                case "month" -> String.valueOf(plugin.getGameMonth(world));
                case "month_name" -> plugin.getMonthName(plugin.getGameMonth(world));
                case "weekday" -> String.valueOf(plugin.getWeekday(world));
                case "weekday_name" -> plugin.getWeekdayName(plugin.getWeekday(world));
                case "season" -> colorize(plugin.getSeasonName(world));
                case "direction" -> colorize(plugin.getDirection(player));
                case "direction_short" -> colorize(plugin.getShortDirection(player));
                default -> "";
            };
        }
    }
}