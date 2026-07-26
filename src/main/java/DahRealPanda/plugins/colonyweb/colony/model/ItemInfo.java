package DahRealPanda.plugins.colonyweb.colony.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Identity of an item as the dashboard shows it: texture key, display name and (for Domum
 * Ornamentum blocks) the material breakdown rendered as tooltip lines.
 */
public class ItemInfo {
    public String itemKey;   // texture key: namespace:path (+ optional #hash)
    public String name;      // display name
    public String material;  // combined DO material names, null when not DO
    public boolean domum;    // true for Domum Ornamentum textured blocks
    public String craftedIn; // e.g. "Architects Cutter", null when unknown
    public boolean craftable; // a colony worker knows a recipe that produces this

    public List<MaterialComponent> components = new ArrayList<>();
}
