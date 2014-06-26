package bg.scelus.routeros.menulister.models;

import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Command {

    public String name;
    public MenuItem parent;
    public String summary = "";
    public ArrayList<Argument> arguments = new ArrayList<>();
    
    public Command(String name, MenuItem parent) {
        this.name = name;
        this.parent = parent;
    }

    @SuppressWarnings("unchecked")
    public JSONObject getJSON() {
        JSONObject result = new JSONObject();
        result.put("name", name);
        result.put("summary", summary);

        JSONArray argumentsArray = new JSONArray();
        for (Argument item : arguments) {
            argumentsArray.add(item.getJSON());
        }
        result.put("arguments", argumentsArray);

        return result;
    }
}
