package bg.scelus.routeros.menulister.models;

import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MenuItem {

    public String name;
    public MenuItem parent = null;
    public String summary = "";
    public String description = "";
    public ArrayList<MenuItem> subMenus = new ArrayList<>();
    public ArrayList<Command> commands = new ArrayList<>();

    public MenuItem(String name) {
        this.name = name;
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

        JSONArray subMenuArray = new JSONArray();
        subMenus.stream().forEach((item) -> {
            subMenuArray.add(item.getJSON());
        });
        result.put("submenus", subMenuArray);

        JSONArray commandsArray = new JSONArray();
        commands.stream().forEach((item) -> {
            commandsArray.add(item.getJSON());
        });
        result.put("commands", commandsArray);

        return result;
    }
}
