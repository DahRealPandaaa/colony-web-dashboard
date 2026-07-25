package DahRealPanda.plugins.untitled1.colony.model;

/**
 * A work order describing what is being built/upgraded and by whom.
 */
public class WorkOrderInfo {
    public int id;
    public String buildingName;
    public String buildingType;
    public int x;
    public int y;
    public int z;
    public int currentLevel;
    public int targetLevel;
    public String action; // BUILD / UPGRADE / REPAIR / REMOVE
    public int builderId = -1;
    public String builderName;
    public double progress; // 0.0 - 1.0
}
