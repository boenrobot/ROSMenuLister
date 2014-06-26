package bg.scelus.routeros.menulister.models;

import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class MenuItem {
	public MenuItem parent = null;
	public int level = 0;
	public String name;
	public String description;
	public ArrayList<MenuItem> subMenus = new ArrayList <MenuItem> ();
	public ArrayList<Command> commands = new ArrayList <Command> ();
	
	@SuppressWarnings("unchecked")
	public JSONObject getJSON() {
		JSONObject result = new JSONObject();
		result.put("name", name);
		result.put("description", description);
		
		JSONArray subMenuArray = new JSONArray();
		for (MenuItem item : subMenus)
			subMenuArray.add(item.getJSON());
		result.put("submenus", subMenuArray);

		JSONArray commandsArray = new JSONArray();
		for (Command item : commands)
			commandsArray.add(item.getJSON());
		result.put("commands", commandsArray);
		
		return result;
	}
}
