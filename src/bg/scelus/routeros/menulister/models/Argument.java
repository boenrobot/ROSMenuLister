package bg.scelus.routeros.menulister.models;

import org.json.simple.JSONObject;

public class Argument {

    public String name;
    public Command parent;
    public String summary = "";
    public String values = null;
    public boolean isUnnamed = false;
    public boolean isSpecial = false;

    public Argument(String name, Command parent) {
        this.name = name;
        this.parent = parent;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSON() {
        JSONObject result = new JSONObject();
        result.put("name", name);

        if (!summary.isEmpty()) {
            result.put("summary", summary);
        }

        if (isUnnamed) {
            result.put("unnamed", isUnnamed);
        }

        if (isSpecial) {
            result.put("keyword", isSpecial);
        }

        if (null != values) {
            result.put("values", values);
        }

        return result;
    }
}
