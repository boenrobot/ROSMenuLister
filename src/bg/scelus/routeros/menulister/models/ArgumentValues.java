package bg.scelus.routeros.menulister.models;

import java.util.EnumSet;
import java.util.HashSet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ArgumentValues {

    public final HashSet<String> enums = new HashSet<>();
    public final EnumSet<ScriptingType> type = EnumSet.noneOf(ScriptingType.class);

    public String raw = "";
    public boolean isScriptConstruct = false;
    public boolean isNegatable = false;

    @SuppressWarnings("unchecked")
    public JSONObject getJSON() {
        JSONObject result = new JSONObject();
        if (!raw.isEmpty()) {
            result.put("raw", raw);
        }

        if (isScriptConstruct) {
            result.put("scriptConstruct", isScriptConstruct);
        }

        if (isNegatable) {
            result.put("negatable", isNegatable);
        }

        if (!type.isEmpty()) {
            JSONArray typeArray = new JSONArray();
            type.stream().forEach((t) -> {
                typeArray.add(t.toString());
            });
            result.put("type", typeArray);
        }

        if (!enums.isEmpty()) {
            JSONArray enumsArray = new JSONArray();
            enumsArray.addAll(enums);
            result.put("enums", enumsArray);
        }

        return result;
    }
}
