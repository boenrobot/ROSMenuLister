package bg.scelus.routeros.menulister.models;

import org.json.simple.JSONObject;

public class Argument {

    public String name;
    public Command parent;
    public String summary = "";
    public boolean isUnnamed = false;
    public boolean isKeyword = false;
    
    public Argument(String name, Command parent) {
        this.name = name;
        this.parent = parent;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSON() {
        JSONObject result = new JSONObject();
        result.put("name", name);
        result.put("summary", summary);
        result.put("unnamed", isUnnamed);
        result.put("keyword", isKeyword);
        return result;
    }
}
