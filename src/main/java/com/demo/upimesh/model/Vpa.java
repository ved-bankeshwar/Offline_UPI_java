package com.demo.upimesh.model;

/**
 * A payment address written as name@bank, for example alice@demo.
 * The constructor checks the shape. The rest of the app stores the text.
 */
public class Vpa {

    private final String name;
    private final String bank;

    public Vpa(String text) {
        if (text == null) {
            throw new IllegalArgumentException("A VPA is required");
        }
        String trimmed = text.trim();
        String[] parts = trimmed.split("@");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("A VPA looks like name@demo");
        }
        if (!parts[1].equalsIgnoreCase("demo")) {
            throw new IllegalArgumentException("This demo only pays @demo accounts");
        }
        this.name = parts[0].toLowerCase();
        this.bank = parts[1].toLowerCase();
    }

    public String text() {
        return name + "@" + bank;
    }
}
