package io.github.lawrencekcleung.pft;

import io.github.lawrencekcleung.pft.cli.Cli;
import io.github.lawrencekcleung.pft.cli.CommandRegistry;

import java.util.List;

/**
 * Entry point for the personal finance tracker.
 *
 * <p>All behaviour lives behind {@link Cli}; this class only builds the production registry and
 * calls {@link System#exit} once with the returned code, so tests never exit the JVM. Commands are
 * registered here as their phases land.</p>
 */
public final class Main {

    public static final String NAME = "pft";

    private Main() {
    }

    public static void main(String[] args) {
        CommandRegistry registry = new CommandRegistry(List.of());
        System.exit(Cli.defaults(registry).run(args));
    }
}
