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
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JRPIndicators extends JavaPlugin implements CommandExecutor, TabCompleter {

    private JRPIndicatorsExpansion placeholderExpansion;
    private int daysPerMonth;
    private int startYear;
    private final Map<String, List<Integer>> seasonMonths = new HashMap<>();
    private final Map<String, String> seasonNames = new HashMap<>();
    private String lastDayPhase = "";
    private String lastWeather = "";
    private String lastSeason = "";
    private String lastZodiac = "";
    private String lastHolidayDate = "";
    private final Map<String, List<String>> dayGreetings = new HashMap<>();
    private final Map<String, List<String>> weatherGreetings = new HashMap<>();
    private final Map<String, List<String>> seasonGreetings = new HashMap<>();
    private final Map<String, List<String>> zodiacGreetings = new HashMap<>();
    private final Map<String, List<String>> holidayGreetings = new HashMap<>();
    private final Map<String, String> holidayNames = new HashMap<>();
    private boolean dayGreetingEnabled;
    private boolean weatherGreetingEnabled;
    private boolean seasonGreetingEnabled;
    private boolean zodiacGreetingEnabled;
    private boolean holidayGreetingEnabled;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        loadSeasons();
        loadGreetings();
        loadHolidays();

        Objects.requireNonNull(getCommand("jrpi")).setExecutor(this);
        Objects.requireNonNull(getCommand("jrpi")).setTabCompleter(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new JRPIndicatorsExpansion(this);
            placeholderExpansion.register();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                checkAllChanges();
            }
        }.runTaskTimer(this, 0L, 20L * 30);
    }

    private void loadConfigValues() {
        daysPerMonth = getConfig().getInt("calendar.months_days_count", 28);
        startYear = getConfig().getInt("calendar.start_year", 1200);
    }

    private void loadSeasons() {
        seasonMonths.clear();
        seasonNames.clear();

        seasonMonths.put("spring", List.of(3, 4, 5));
        seasonMonths.put("summer", List.of(6, 7, 8));
        seasonMonths.put("autumn", List.of(9, 10, 11));
        seasonMonths.put("winter", List.of(12, 1, 2));

        seasonNames.put("spring", getConfig().getString("seasons.spring.name", "&aВесна"));
        seasonNames.put("summer", getConfig().getString("seasons.summer.name", "&eЛето"));
        seasonNames.put("autumn", getConfig().getString("seasons.autumn.name", "&6Осень"));
        seasonNames.put("winter", getConfig().getString("seasons.winter.name", "&bЗима"));

        if (getConfig().isConfigurationSection("seasons")) {
            for (String key : Objects.requireNonNull(getConfig().getConfigurationSection("seasons")).getKeys(false)) {
                String path = "seasons." + key + ".";
                List<Integer> months = getConfig().getIntegerList(path + "months");
                String name = getConfig().getString(path + "name", key);
                if (!months.isEmpty()) {
                    seasonMonths.put(key.toLowerCase(), months);
                    seasonNames.put(key.toLowerCase(), name);
                }
            }
        }
    }

    private void loadGreetings() {
        dayGreetingEnabled = getConfig().getBoolean("day-greeting.enabled", false);
        weatherGreetingEnabled = getConfig().getBoolean("weather-greeting.enabled", false);
        seasonGreetingEnabled = getConfig().getBoolean("season-greeting.enabled", false);
        zodiacGreetingEnabled = getConfig().getBoolean("zodiac_year_greeting.enabled", false);
        holidayGreetingEnabled = getConfig().getBoolean("holiday_greeting.enabled", false);

        dayGreetings.clear();
        weatherGreetings.clear();
        seasonGreetings.clear();
        zodiacGreetings.clear();
        holidayGreetings.clear();

        for (String phase : List.of("morning", "day", "evening", "night")) {
            List<String> msgs = getConfig().getStringList("day-greeting." + phase);
            if (!msgs.isEmpty()) dayGreetings.put(phase, msgs);
        }

        for (String w : List.of("sun", "rain", "snow", "storm")) {
            List<String> msgs = getConfig().getStringList("weather-greeting." + w);
            if (!msgs.isEmpty()) weatherGreetings.put(w, msgs);
        }

        for (String s : List.of("spring", "summer", "autumn", "winter")) {
            List<String> msgs = getConfig().getStringList("season-greeting." + s);
            if (!msgs.isEmpty()) seasonGreetings.put(s, msgs);
        }

        if (getConfig().isConfigurationSection("zodiac_year_greeting")) {
            for (String animal : Objects.requireNonNull(getConfig().getConfigurationSection("zodiac_year_greeting")).getKeys(false)) {
                List<String> msgs = getConfig().getStringList("zodiac_year_greeting." + animal);
                if (!msgs.isEmpty()) {
                    zodiacGreetings.put(animal, msgs);
                }
            }
        }

        if (getConfig().isConfigurationSection("holiday_greeting")) {
            for (String date : Objects.requireNonNull(getConfig().getConfigurationSection("holiday_greeting")).getKeys(false)) {
                List<String> msgs = getConfig().getStringList("holiday_greeting." + date);
                if (!msgs.isEmpty()) {
                    holidayGreetings.put(date, msgs);
                }
            }
        }
    }

    private void loadHolidays() {
        holidayNames.clear();
        if (getConfig().isConfigurationSection("holidays.dates")) {
            for (String key : Objects.requireNonNull(getConfig().getConfigurationSection("holidays.dates")).getKeys(false)) {
                String name = getConfig().getString("holidays.dates." + key, "");
                if (!name.isEmpty()) {
                    holidayNames.put(key, colorize(name));
                }
            }
        }
    }

    private void checkAllChanges() {
        World world = getMainWorld();
        if (world == null) return;

        checkDayPhaseChange(world);
        checkWeatherChange(world);
        checkSeasonChange(world);
        checkZodiacChange(world);
        checkHolidayChange(world);
    }

    private @Nullable World getMainWorld() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private void checkDayPhaseChange(World world) {
        if (!dayGreetingEnabled) return;
        String current = getCurrentDayPhaseKey(world);
        if (!current.equals(lastDayPhase) && !current.isEmpty()) {
            List<String> msgs = dayGreetings.get(current);
            if (msgs != null && !msgs.isEmpty()) {
                Bukkit.broadcastMessage(colorize(msgs.get(random.nextInt(msgs.size()))));
            }
            lastDayPhase = current;
        }
    }

    private void checkWeatherChange(World world) {
        if (!weatherGreetingEnabled) return;
        String current = getCurrentWeatherKey(world);
        if (!current.equals(lastWeather) && !current.isEmpty()) {
            List<String> msgs = weatherGreetings.get(current);
            if (msgs != null && !msgs.isEmpty()) {
                Bukkit.broadcastMessage(colorize(msgs.get(random.nextInt(msgs.size()))));
            }
            lastWeather = current;
        }
    }

    private void checkSeasonChange(World world) {
        if (!seasonGreetingEnabled) return;
        String currentSeason = getSeasonKey(world);
        if (!currentSeason.equals(lastSeason) && !lastSeason.isEmpty()) {
            List<String> msgs = seasonGreetings.get(currentSeason);
            if (msgs != null && !msgs.isEmpty()) {
                Bukkit.broadcastMessage(colorize(msgs.get(random.nextInt(msgs.size()))));
            }
        }
        lastSeason = currentSeason;
    }

    private void checkZodiacChange(World world) {
        if (!zodiacGreetingEnabled) return;
        String currentZodiac = getZodiacAnimal(world);
        if (!currentZodiac.equals(lastZodiac) && !currentZodiac.isEmpty()) {
            List<String> messages = zodiacGreetings.get(currentZodiac);
            if (messages != null && !messages.isEmpty()) {
                String msg = messages.get(random.nextInt(messages.size()));
                Bukkit.broadcastMessage(colorize(msg));
            }
            lastZodiac = currentZodiac;
        }
    }

    private void checkHolidayChange(World world) {
        if (!holidayGreetingEnabled) return;
        String currentDateKey = getGameMonth(world) + "-" + getGameDay(world);
        if (!currentDateKey.equals(lastHolidayDate) && !lastHolidayDate.isEmpty()) {
            List<String> messages = holidayGreetings.get(currentDateKey);
            if (messages != null && !messages.isEmpty()) {
                String msg = messages.get(random.nextInt(messages.size()));
                Bukkit.broadcastMessage(colorize(msg));
            }
        }
        lastHolidayDate = currentDateKey;
    }

    private String getCurrentDayPhaseKey(World world) {
        long ticks = world.getTime();
        long adjusted = (ticks + 6000) % 24000;
        int hour = (int) (adjusted / 1000);

        if (hour >= 4 && hour < 12) return "morning";
        if (hour >= 12 && hour < 18) return "day";
        if (hour >= 18 && hour < 22) return "evening";
        return "night";
    }

    private String getCurrentWeatherKey(World world) {
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

    public int getGameYear(World world) {
        long totalDays = world.getFullTime() / 24000L;
        return (int) (totalDays / (daysPerMonth * 12L)) + startYear;
    }

    public String getZodiacAnimal(World world) {
        int year = getGameYear(world);
        int index = ((year - startYear) % 12 + 12) % 12 + 1;
        return getConfig().getString("calendar.zodiac_animals.cycle." + index, "?");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            List<String> help = getConfig().getStringList("messages.help");
            for (String line : help) {
                sender.sendMessage(colorize(line));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("jrpindicators.admin")) {
                sender.sendMessage(colorize(getConfig().getString("messages.no-permission", "")));
                return true;
            }

            reloadConfig();
            loadConfigValues();
            loadSeasons();
            loadGreetings();
            loadHolidays();

            lastDayPhase = "";
            lastWeather = "";
            lastSeason = "";
            lastZodiac = "";
            lastHolidayDate = "";

            sender.sendMessage(colorize(getConfig().getString("messages.reload-success", "")));
            return true;
        }

        if (sub.equals("set") && args.length >= 3) {
            if (!sender.hasPermission("jrpindicators.admin")) {
                sender.sendMessage(colorize(getConfig().getString("messages.no-permission", "")));
                return true;
            }

            String type = args[1].toLowerCase();
            String valueStr = args[2];

            World world = getMainWorld();
            if (world == null) {
                sender.sendMessage(colorize("&cМир не найден!"));
                return true;
            }

            long totalDays = world.getFullTime() / 24000L;
            int currentDay = getGameDay(world);
            int currentMonth = getGameMonth(world);
            int currentYear = getGameYear(world);

            long newTotalDays;

            switch (type) {
                case "day" -> {
                    int value;
                    try {
                        value = Integer.parseInt(valueStr);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(colorize(getConfig().getString("messages.invalid-number", "&cНеверное число!")));
                        return true;
                    }
                    if (value < 1 || value > daysPerMonth) {
                        sender.sendMessage(colorize(getConfig().getString("messages.invalid-day", "&cДень должен быть от 1 до %days%").replace("%days%", String.valueOf(daysPerMonth))));
                        return true;
                    }
                    newTotalDays = (totalDays / daysPerMonth) * daysPerMonth + (value - 1);
                    sender.sendMessage(colorize(getConfig().getString("messages.set-day-success", "&aДень установлен на &f%value%").replace("%value%", String.valueOf(value))));
                }
                case "month" -> {
                    int value;
                    try {
                        value = Integer.parseInt(valueStr);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(colorize(getConfig().getString("messages.invalid-number", "&cНеверное число!")));
                        return true;
                    }
                    if (value < 1 || value > 12) {
                        sender.sendMessage(colorize(getConfig().getString("messages.invalid-month", "&cМесяц должен быть от 1 до 12!")));
                        return true;
                    }
                    newTotalDays = ((currentYear - startYear) * 12L + (value - 1)) * daysPerMonth + (currentDay - 1);
                    sender.sendMessage(colorize(getConfig().getString("messages.set-month-success", "&aМесяц установлен на &f%value%").replace("%value%", String.valueOf(value))));
                }
                case "year" -> {
                    int value;
                    try {
                        value = Integer.parseInt(valueStr);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(colorize(getConfig().getString("messages.invalid-number", "&cНеверное число!")));
                        return true;
                    }
                    newTotalDays = ((value - startYear) * 12L) * daysPerMonth + (currentMonth - 1L) * daysPerMonth + (currentDay - 1);
                    sender.sendMessage(colorize(getConfig().getString("messages.set-year-success", "&aГод установлен на &f%value%").replace("%value%", String.valueOf(value))));
                }
                default -> {
                    sender.sendMessage(colorize(getConfig().getString("messages.invalid-set", "&cНеверный тип: day, month или year")));
                    return true;
                }
            }

            world.setFullTime(newTotalDays * 24000L);
            return true;
        }

        sender.sendMessage(colorize(getConfig().getString("messages.unknown-command", "&cНеизвестная команда. Используй /jrpi")));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("set");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            completions.add("day");
            completions.add("month");
            completions.add("year");
        }

        return completions;
    }

    public String getDayPhaseFormatted(World world) {
        return colorize(getConfig().getString("day-type." + getCurrentDayPhaseKey(world), ""));
    }

    public String getFormattedTime(World world) {
        long ticks = world.getTime();
        long adjusted = (ticks + 6000) % 24000;
        int hours = (int) (adjusted / 1000);
        int minutes = (int) ((adjusted % 1000) * 60 / 1000);
        String fmt = getConfig().getString("time-format", "HH:mm");
        return fmt
                .replace("HH", String.format("%02d", hours))
                .replace("H", String.valueOf(hours))
                .replace("mm", String.format("%02d", minutes))
                .replace("m", String.valueOf(minutes));
    }

    public String getWeatherFormatted(Player player) {
        return colorize(getWeather(player));
    }

    private String getWeather(Player player) {
        World world = player.getWorld();
        if (!world.hasStorm()) return getConfig().getString("weather.sun", "Ясно");
        if (world.isThundering()) return getConfig().getString("weather.storm", "Гроза");
        float temp = (float) player.getLocation().getBlock().getTemperature();
        return temp < 0.15f ? getConfig().getString("weather.snow", "Снег") : getConfig().getString("weather.rain", "Дождь");
    }

    public int getGameDay(World world) {
        long totalDays = world.getFullTime() / 24000L;
        return (int) ((totalDays % daysPerMonth) + 1);
    }

    public int getGameMonth(World world) {
        long totalDays = world.getFullTime() / 24000L;
        return (int) ((totalDays / daysPerMonth) % 12 + 1);
    }

    public String getMonthName(int month) {
        return getConfig().getString("calendar.months." + month, String.valueOf(month));
    }

    public int getWeekday(World world) {
        long totalDays = world.getFullTime() / 24000L;
        return (int) ((totalDays + 6) % 7 + 1);
    }

    public String getWeekdayName(int weekday) {
        return getConfig().getString("calendar.weekdays." + weekday, String.valueOf(weekday));
    }

    public String getSeasonName(World world) {
        String key = getSeasonKey(world);
        return seasonNames.getOrDefault(key, "&7Неизвестно");
    }

    public String getDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        int index = Math.round(yaw / 45f) % 8;
        return getConfig().getString("directions.direction_" + index, "Неизвестно");
    }

    public String getShortDirection(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        int index = Math.round(yaw / 45f) % 8;
        return getConfig().getString("directions.short_" + index, "?");
    }

    public static String colorize(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private class JRPIndicatorsExpansion extends PlaceholderExpansion {

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
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return null;

            World world = player.getWorld();

            return switch (params.toLowerCase()) {
                case "day" -> String.valueOf(plugin.getGameDay(world));
                case "day_type" -> plugin.getDayPhaseFormatted(world);
                case "time" -> plugin.getFormattedTime(world);
                case "weather" -> plugin.getWeatherFormatted(player);
                case "month" -> String.valueOf(plugin.getGameMonth(world));
                case "month_name" -> plugin.getMonthName(plugin.getGameMonth(world));
                case "weekday" -> String.valueOf(plugin.getWeekday(world));
                case "weekday_name" -> plugin.getWeekdayName(plugin.getWeekday(world));
                case "season" -> colorize(plugin.getSeasonName(world));
                case "direction" -> colorize(plugin.getDirection(player));
                case "direction_short" -> colorize(plugin.getShortDirection(player));
                case "year" -> String.valueOf(plugin.getGameYear(world));
                case "zodiac" -> colorize(plugin.getZodiacAnimal(world));
                case "holiday_name" -> {
                    String key = plugin.getGameMonth(world) + "-" + plugin.getGameDay(world);
                    yield holidayNames.getOrDefault(key, "");
                }
                default -> null;
            };
        }
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
    }
}
