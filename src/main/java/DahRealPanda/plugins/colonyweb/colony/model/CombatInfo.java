package DahRealPanda.plugins.colonyweb.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Colony defence: raid pressure, the guard roster and the buildings backing them.
 */
public class CombatInfo {
    public boolean raidsPossible;
    public boolean underAttack;
    public int nightsSinceRaid;
    public int raidLevel;        // MineColonies' colony raid level (raid difficulty scaling)
    public boolean spiesEnabled;

    public int guardCount;
    public int guardCapacity;    // total guard slots across guard buildings
    public double averageGuardLevel;
    public double averageHealthPct;
    public int graves;           // unclaimed graves — citizens that died and need burying

    public List<Guard> guards = new ArrayList<>();
    public List<Post> posts = new ArrayList<>();
    public List<Event> events = new ArrayList<>();

    /** A citizen with a combat job. */
    public static class Guard {
        public int id;
        public String name;
        public String job;
        public String jobType;
        public int level;        // job-relevant skill level
        public double health;
        public double maxHealth;
        public boolean spawned;
        public String building;
        public int x;
        public int y;
        public int z;
    }

    /** A guard tower / barracks and how well it is staffed. */
    public static class Post {
        public int id;
        public String name;
        public String type;
        public String blockId;
        public int level;
        public int assigned;
        public int capacity;
        public int x;
        public int y;
        public int z;
    }

    /** An active colony event — usually an ongoing raid. */
    public static class Event {
        public int id;
        public String name;
        public String status;
        public int x;
        public int y;
        public int z;
    }
}
