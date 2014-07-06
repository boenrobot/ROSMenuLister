package bg.scelus.routeros.menulister.models;

public enum ScriptingType {

    /**
     * Pseudo type, indicating that the value is not a "value" per se, but a
     * script.
     */
    _CODE_,
    STR,
    BOOL,
    NUM,
    TIME,
    IP,
    IP_PREFIX,
    IP6,
    IP6_PREFIX;

    /**
     *
     * @return The name of the type, as defined by MikroTik. Pseudo types are
     * rerurned surrounded with brackets.
     */
    @Override
    public String toString() {
        String name = name().toLowerCase();
        if (name.startsWith("_") && name.endsWith("_")) {
            name = "(" + name.substring(1, name.length() - 1) + ")";
        }
        return name.replace('_', '-');
    }
}
