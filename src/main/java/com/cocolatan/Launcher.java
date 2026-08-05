package com.cocolatan;

/**
 * Packaging entry point. Does NOT extend {@link javafx.application.Application} so that the
 * jpackage launcher starts a plain main and JavaFX is loaded from the classpath, avoiding the
 * "Missing JavaFX application class" and "JavaFX runtime components are missing" checks.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        CocolatanApp.main(args);
    }
}
