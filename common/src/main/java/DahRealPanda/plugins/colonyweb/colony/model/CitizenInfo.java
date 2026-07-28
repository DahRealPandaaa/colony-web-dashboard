package DahRealPanda.plugins.colonyweb.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A colony citizen with the stats, skills and happiness modifiers the game tracks for them.
 *
 * <p>Inventories are deliberately not part of this DTO — they are cached separately and
 * served by {@code /api/colony/{id}/citizen/{citizenId}} so the citizen list stays light.</p>
 */
public class CitizenInfo {
    public int id;
    public String name;

    public String job;          // readable job name, "Unemployed" when idle
    public String jobType;      // job registry id, null when unemployed
    public String jobIcon;      // texture key for the job's hut block (icon source)
    public boolean child;
    public boolean female;

    public double health;
    public double maxHealth;
    public double saturation;   // 0-20
    public double happiness;    // 0-10
    public boolean spawned;     // the entity is currently loaded in the world

    public int x;
    public int y;
    public int z;

    public String workBuilding;
    public int workBuildingId = -1;
    public String homeBuilding;
    public int homeBuildingId = -1;
    public String status;       // current activity, when MineColonies exposes one

    public String primarySkill;
    public String secondarySkill;
    public int skillTotal;

    public int inventoryUsed;
    public int inventorySize;

    public List<Skill> skills = new ArrayList<>();
    public List<Modifier> modifiers = new ArrayList<>();

    /** One of the eleven MineColonies skills. */
    public static class Skill {
        public String name;
        public int level;
        public double xp;
        public String role; // "primary" / "secondary" / null

        public Skill(String name, int level, double xp) {
            this.name = name;
            this.level = level;
            this.xp = xp;
        }
    }

    /** A happiness modifier — what the game calls the citizen's perks and grievances. */
    public static class Modifier {
        public String name;
        public double factor; // >1 positive, <1 negative

        public Modifier(String name, double factor) {
            this.name = name;
            this.factor = factor;
        }
    }
}
