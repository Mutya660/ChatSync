package chatsync;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team (party) system: create, invite, chat, customize name/color.
 * Data stored in teams.yml when teams.persist is true.
 */
public class TeamManager {

    public static final class Team {
        public final String id;
        public String name;
        public String color; // legacy code like &b
        public String symbol; // chat prefix e.g. # $ ~
        public UUID owner;
        public final LinkedHashSet<UUID> coOwners = new LinkedHashSet<>();
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        public Team(String id, String name, String color, UUID owner, String symbol) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.owner = owner;
            this.symbol = symbol;
            this.members.add(owner);
        }

        public boolean isOwner(UUID u) {
            return owner != null && owner.equals(u);
        }

        /** Owner or co-owner. */
        public boolean isLeader(UUID u) {
            return isOwner(u) || coOwners.contains(u);
        }
    }

    public static final class Invite {
        public final String teamId;
        public final UUID inviter;
        public final long expiresAt;

        public Invite(String teamId, UUID inviter, long expiresAt) {
            this.teamId = teamId;
            this.inviter = inviter;
            this.expiresAt = expiresAt;
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTeam = new ConcurrentHashMap<>();
    /** invitee → invite */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getConfig().getBoolean("teams.persist", true)) load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("teams.enabled", true);
    }

    public Team getTeam(String id) { return teams.get(id); }

    public Team getTeamOf(UUID uuid) {
        String id = playerTeam.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public Collection<Team> allTeams() { return Collections.unmodifiableCollection(teams.values()); }

    public int teamCount() { return teams.size(); }

    public String create(Player owner, String name) {
        if (!enabled()) return "disabled";
        if (getTeamOf(owner.getUniqueId()) != null) return "already_in";
        int maxTeams = plugin.getConfig().getInt("teams.max_teams", 50);
        if (teams.size() >= maxTeams) return "max_teams";
        int maxLen = plugin.getConfig().getInt("teams.max_name_length", 16);
        if (name == null || name.isBlank()) return "bad_name";
        name = name.trim();
        if (name.length() > maxLen) return "name_long";
        if (!name.matches("[\\w\\-а-яА-ЯёЁ]{2," + maxLen + "}")) return "bad_name";
        for (Team t : teams.values()) {
            if (t.name.equalsIgnoreCase(name)) return "name_taken";
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        String color = plugin.getConfig().getString("teams.default_color", "&b");
        String symbol = nextFreeSymbol();
        Team team = new Team(id, name, color, owner.getUniqueId(), symbol);
        teams.put(id, team);
        playerTeam.put(owner.getUniqueId(), id);
        save();
        return "ok";
    }

    /** Default symbol from config, or next free from a pool. */
    private String nextFreeSymbol() {
        String def = plugin.getConfig().getString("teams.default_symbol", "#");
        if (def != null && !def.isEmpty() && findTeamBySymbol(def) == null) return def;
        String pool = plugin.getConfig().getString("teams.symbol_pool", "#$~@%^&*");
        if (pool == null || pool.isEmpty()) pool = "#$~@%^&*";
        for (int i = 0; i < pool.length(); i++) {
            String s = String.valueOf(pool.charAt(i));
            if (findTeamBySymbol(s) == null) return s;
        }
        // fallback unique
        for (int i = 1; i < 100; i++) {
            String s = "#" + i;
            if (findTeamBySymbol(s) == null) return s;
        }
        return "#";
    }

    public Team findTeamBySymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) return null;
        for (Team t : teams.values()) {
            if (symbol.equals(t.symbol)) return t;
        }
        return null;
    }

    public String setSymbol(Player owner, String symbol) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isLeader(owner.getUniqueId())) return "not_owner";
        if (symbol == null || symbol.isBlank()) return "bad_symbol";
        symbol = symbol.trim();
        if (symbol.contains(" ")) return "bad_symbol";
        if (symbol.length() > 3) return "bad_symbol";
        // cannot clash with global chat symbol
        String globalSym = plugin.getConfig().getString("chat.global.symbol", "!");
        if (symbol.equals(globalSym)) return "clash_global";
        Team other = findTeamBySymbol(symbol);
        if (other != null && other != team) return "taken";
        team.symbol = symbol;
        save();
        return "ok";
    }


    public String invite(Player inviter, Player target) {
        Team team = getTeamOf(inviter.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isLeader(inviter.getUniqueId())) return "not_owner";
        if (getTeamOf(target.getUniqueId()) != null) return "target_in_team";
        int maxMembers = plugin.getConfig().getInt("teams.max_members", 8);
        if (team.members.size() >= maxMembers) return "full";
        if (inviter.getUniqueId().equals(target.getUniqueId())) return "self";
        int timeout = plugin.getConfig().getInt("teams.invite_timeout", 60);
        invites.put(target.getUniqueId(),
                new Invite(team.id, inviter.getUniqueId(), System.currentTimeMillis() + timeout * 1000L));
        return "ok";
    }

    public Invite getInvite(UUID invitee) {
        Invite inv = invites.get(invitee);
        if (inv == null) return null;
        if (System.currentTimeMillis() > inv.expiresAt) {
            invites.remove(invitee);
            return null;
        }
        return inv;
    }

    public String accept(Player player) {
        Invite inv = getInvite(player.getUniqueId());
        if (inv == null) return "no_invite";
        if (getTeamOf(player.getUniqueId()) != null) return "already_in";
        Team team = teams.get(inv.teamId);
        if (team == null) {
            invites.remove(player.getUniqueId());
            return "team_gone";
        }
        int maxMembers = plugin.getConfig().getInt("teams.max_members", 8);
        if (team.members.size() >= maxMembers) return "full";
        team.members.add(player.getUniqueId());
        playerTeam.put(player.getUniqueId(), team.id);
        invites.remove(player.getUniqueId());
        save();
        return "ok";
    }

    public String deny(Player player) {
        if (getInvite(player.getUniqueId()) == null) return "no_invite";
        invites.remove(player.getUniqueId());
        return "ok";
    }

    public String leave(Player player) {
        Team team = getTeamOf(player.getUniqueId());
        if (team == null) return "no_team";
        if (team.owner.equals(player.getUniqueId())) {
            // owner leaves → promote co-owner or first member, else disband
            team.members.remove(player.getUniqueId());
            team.coOwners.remove(player.getUniqueId());
            playerTeam.remove(player.getUniqueId());
            if (team.members.isEmpty()) {
                teams.remove(team.id);
            } else {
                UUID next = null;
                for (UUID c : team.coOwners) { if (team.members.contains(c)) { next = c; break; } }
                if (next == null) next = team.members.iterator().next();
                team.owner = next;
                team.coOwners.remove(next);
            }
            save();
            return "ok_owner";
        }
        team.members.remove(player.getUniqueId());
        team.coOwners.remove(player.getUniqueId());
        playerTeam.remove(player.getUniqueId());
        save();
        return "ok";
    }

    public String kick(Player owner, UUID target) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isLeader(owner.getUniqueId())) return "not_owner";
        if (target.equals(owner.getUniqueId())) return "self";
        if (team.isOwner(target)) return "cannot_kick_owner";
        if (!team.members.contains(target)) return "not_member";
        team.members.remove(target);
        team.coOwners.remove(target);
        playerTeam.remove(target);
        save();
        return "ok";
    }

    public String disband(Player owner) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.owner.equals(owner.getUniqueId())) return "not_owner";
        for (UUID m : new ArrayList<>(team.members)) playerTeam.remove(m);
        teams.remove(team.id);
        save();
        return "ok";
    }

    public String rename(Player owner, String name) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isLeader(owner.getUniqueId())) return "not_owner";
        int maxLen = plugin.getConfig().getInt("teams.max_name_length", 16);
        if (name == null || name.isBlank() || name.length() > maxLen) return "bad_name";
        name = name.trim();
        if (!name.matches("[\\w\\-а-яА-ЯёЁ]{2," + maxLen + "}")) return "bad_name";
        for (Team t : teams.values()) {
            if (t != team && t.name.equalsIgnoreCase(name)) return "name_taken";
        }
        team.name = name;
        save();
        return "ok";
    }

    public String setColor(Player owner, String color) {
        if (!plugin.getConfig().getBoolean("teams.allow_color_change", true)) return "disabled";
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isLeader(owner.getUniqueId())) return "not_owner";
        if (color == null || color.isBlank()) return "bad_color";
        color = color.trim().replace("§", "&");
        // allow &0-9a-f, &k-o, &r and combinations e.g. &c&l
        if (!color.startsWith("&")) color = "&" + color;
        String lower = color.toLowerCase();
        if (!lower.matches("(&[0-9a-fk-or])+")) return "bad_color";
        team.color = lower;
        save();
        return "ok";
    }


    /** Transfer ownership to a member. Only primary owner. */
    public String transfer(Player owner, UUID target) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isOwner(owner.getUniqueId())) return "not_owner";
        if (!team.members.contains(target)) return "not_member";
        if (target.equals(owner.getUniqueId())) return "self";
        team.coOwners.remove(target);
        team.coOwners.add(team.owner); // old owner becomes co-owner
        team.owner = target;
        save();
        return "ok";
    }

    /** Add co-owner. Only primary owner. */
    public String promote(Player owner, UUID target) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isOwner(owner.getUniqueId())) return "not_owner";
        if (!team.members.contains(target)) return "not_member";
        if (target.equals(owner.getUniqueId())) return "self";
        if (team.coOwners.contains(target)) return "already_co";
        int maxCo = plugin.getConfig().getInt("teams.max_co_owners", 3);
        if (team.coOwners.size() >= maxCo) return "max_co";
        team.coOwners.add(target);
        save();
        return "ok";
    }

    /** Remove co-owner. Only primary owner. */
    public String demote(Player owner, UUID target) {
        Team team = getTeamOf(owner.getUniqueId());
        if (team == null) return "no_team";
        if (!team.isOwner(owner.getUniqueId())) return "not_owner";
        if (!team.coOwners.contains(target)) return "not_co";
        team.coOwners.remove(target);
        save();
        return "ok";
    }

    public void broadcast(Team team, String message) {
        for (UUID id : team.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    public void onQuit(UUID uuid) {
        invites.remove(uuid);
    }

    private void load() {
        File f = new File(plugin.getDataFolder(), "teams.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("teams");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) continue;
            try {
                UUID owner = UUID.fromString(t.getString("owner", ""));
                String name = t.getString("name", id);
                String color = t.getString("color", "&b");
                String symbol = t.getString("symbol", "#");
                Team team = new Team(id, name, color, owner, symbol);
                team.members.clear();
                for (String s : t.getStringList("members")) {
                    try {
                        UUID u = UUID.fromString(s);
                        team.members.add(u);
                        playerTeam.put(u, id);
                    } catch (Exception ignored) {}
                }
                for (String s : t.getStringList("co_owners")) {
                    try {
                        UUID u = UUID.fromString(s);
                        if (team.members.contains(u) && !u.equals(owner)) team.coOwners.add(u);
                    } catch (Exception ignored) {}
                }
                if (!team.members.contains(owner)) {
                    team.members.add(owner);
                    playerTeam.put(owner, id);
                }
                teams.put(id, team);
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        if (!plugin.getConfig().getBoolean("teams.persist", true)) return;
        File f = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration y = new YamlConfiguration();
        for (Team team : teams.values()) {
            String path = "teams." + team.id;
            y.set(path + ".name", team.name);
            y.set(path + ".color", team.color);
            y.set(path + ".symbol", team.symbol);
            y.set(path + ".owner", team.owner.toString());
            List<String> members = new ArrayList<>();
            for (UUID u : team.members) members.add(u.toString());
            y.set(path + ".members", members);
            List<String> cos = new ArrayList<>();
            for (UUID u : team.coOwners) cos.add(u.toString());
            y.set(path + ".co_owners", cos);
        }
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save teams.yml: " + e.getMessage());
        }
    }
}
