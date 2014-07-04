package bg.scelus.routeros.menulister.models;

import org.json.simple.JSONObject;

public class Argument extends Node {

    public final Command parent;

    public String values = null;
    public boolean isUnnamed = false;
    public boolean isSpecial = false;

    public Argument(String name, Command parent) {
        super(name);
        this.parent = parent;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSON() {
        JSONObject result = new JSONObject();
        result.put("name", name);

        if (!summary.isEmpty()) {
            result.put("summary", summary);
        }

        if (!description.isEmpty()) {
            result.put("description", description);
        }

        if (isUnnamed) {
            result.put("unnamed", isUnnamed);
        }

        if (isSpecial) {
            result.put("special", isSpecial);
        }

        if (null != values) {
            result.put("values", values);
        }

        return result;
    }
}
