package DahRealPanda.plugins.untitled1.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A colony building and (when applicable) the resources required to build/upgrade it.
 */
public class BuildingInfo {
    public int id;
    public String name;
    public String type;
    public String kind = "building"; // "building" or "decoration"
    public int level;
    public int x;
    public int y;
    public int z;

    public boolean beingBuilt;
    public int workOrderId = -1;

    public List<ResourceEntry> required = new ArrayList<>();
}
