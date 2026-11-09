package io.github.lawrencekcleung.pft;

import io.github.lawrencekcleung.pft.cli.Cli;

import java.io.PrintWriter;
import java.time.Clock;

/**
 * Entry point for the personal finance tracker.
 *
 * <p>Picocli owns parsing and dispatch; this class only wires the production streams, clock and
 * environment, then calls {@link System#exit} once with the returned code.</p>
 */
public final class Main {

    public static final String NAME = "pft";

    private Main() {
    }

    public static void main(String[] args) {
        Cli cli = Cli.create(Clock.systemDefaultZone(), System.getenv(),
                Cli.DEFAULT_DATA_PATH,
                new PrintWriter(System.out, true),
                new PrintWriter(System.err, true));
        System.exit(cli.run(args));
    }
}
