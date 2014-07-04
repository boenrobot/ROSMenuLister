package bg.scelus.routeros.menulister.models;

public abstract class MenuItem extends Node {

    public final Menu parent;

    public MenuItem(String name, Menu parent) {
        super(name);
        this.parent = parent;
    }
}
