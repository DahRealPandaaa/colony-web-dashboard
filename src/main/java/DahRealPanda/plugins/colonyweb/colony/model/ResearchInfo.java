package DahRealPanda.plugins.colonyweb.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The colony's university research: every branch, what is finished, what is running.
 */
public class ResearchInfo {
    public List<Branch> branches = new ArrayList<>();
    public int completed;
    public int inProgress;
    public int total;
    public boolean available; // false when MineColonies exposes no research tree

    /** One research branch (e.g. Technology, Civilian, Combat). */
    public static class Branch {
        public String id;
        public String name;
        public int completed;
        public int inProgress;
        public int total;
        public List<Entry> researches = new ArrayList<>();
    }

    /** A single research node and its state in this colony. */
    public static class Entry {
        public String id;
        public String name;
        public String branch;
        public int depth;
        public String state;       // COMPLETED / IN_PROGRESS / NOT_STARTED
        public int progress;
        public int maxProgress;
        public List<String> effects = new ArrayList<>();
        public List<String> requirements = new ArrayList<>();
        public List<ItemCount> cost = new ArrayList<>();
    }
}
