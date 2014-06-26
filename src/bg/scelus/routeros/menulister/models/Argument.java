package bg.scelus.routeros.menulister.models;

import org.json.simple.JSONObject;

public class Argument {
	public String name;
	public String description;
	public boolean optional;
	public Command parent;
	public boolean keyword;
	
	@SuppressWarnings("unchecked")
	public JSONObject getJSON() {
		JSONObject result = new JSONObject();
		result.put("name", name);
		result.put("description", description);
		result.put("optional", optional);
		result.put("keyword", keyword);
		return result;
	}
}
