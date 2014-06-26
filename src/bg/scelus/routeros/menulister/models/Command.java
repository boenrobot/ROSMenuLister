package bg.scelus.routeros.menulister.models;

import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Command {
	public MenuItem parent;
	public String name;
	public String description;
	public ArrayList <Argument> arguments = new ArrayList <Argument> ();
	
	@SuppressWarnings("unchecked")
	public JSONObject getJSON() {
		JSONObject result = new JSONObject();
		result.put("name", name);
		result.put("description", description);
		
		JSONArray argumentsArray = new JSONArray();
		for (Argument item : arguments)
			argumentsArray.add(item.getJSON());
		result.put("arguments", argumentsArray);

		return result;
	}
}
