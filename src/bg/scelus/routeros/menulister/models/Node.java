package bg.scelus.routeros.menulister.models;

public abstract class Node {

    public final String name;

    public String summary = "";
    public String description = "";

    public Node(String name) {
        this.name = name;
    }
}
