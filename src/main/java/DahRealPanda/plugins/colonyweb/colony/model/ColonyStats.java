package DahRealPanda.plugins.colonyweb.colony.model;

/**
 * Headline numbers for a colony, shown on the overview tab.
 */
public class ColonyStats {
    public int citizens;
    public int maxCitizens;
    public int children;
    public int unemployed;
    public double happiness;      // colony-wide average, 0-10
    public double saturation;     // average citizen saturation, 0-20

    public int buildings;
    public int decorations;
    public int workOrders;
    public int builders;
    public int guards;

    public int warehouseTypes;    // distinct stacks in the warehouse
    public int warehouseItems;    // total item count in the warehouse

    public int researchCompleted;
    public int researchInProgress;

    public boolean raided;
    public int nightsSinceRaid;
}
