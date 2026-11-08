package io.github.lawrencekcleung.pft.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryTest {

    private static CommandDefinition command(String name) {
        return new CommandDefinition(name, "does " + name, List.of(), (args, ctx) -> { });
    }

    @Test
    void findsARegisteredCommand() {
        CommandDefinition add = command("add");
        CommandRegistry registry = new CommandRegistry(List.of(add));

        assertSame(add, registry.find("add").orElseThrow());
    }

    @Test
    void unknownCommandIsEmpty() {
        CommandRegistry registry = new CommandRegistry(List.of(command("add")));

        assertTrue(registry.find("delete").isEmpty());
    }

    @Test
    void duplicateNamesAreRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new CommandRegistry(List.of(command("add"), command("add"))));

        assertEquals("duplicate command: add", e.getMessage());
    }

    @Test
    void keepsRegistrationOrder() {
        CommandRegistry registry = new CommandRegistry(
                List.of(command("add"), command("list"), command("delete")));

        assertEquals(List.of("add", "list", "delete"),
                registry.all().stream().map(CommandDefinition::name).toList());
    }

    @Test
    void defaultsRegistersTheShippedCommandsInOrder() {
        assertEquals(List.of("add", "list"),
                CommandRegistry.defaults().all().stream()
                        .map(CommandDefinition::name).toList());
    }

    @Test
    void commandOptionsCannotShadowAGlobal() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new CommandDefinition("add", "adds", List.of(
                        new OptionSpec("help", "help", OptionType.FLAG, false, null)),
                        (args, ctx) -> { }));

        assertEquals("option --help is reserved for the CLI", e.getMessage());
    }

    @Test
    void allOptionsAppendsTheGlobals() {
        CommandDefinition add = new CommandDefinition("add", "adds",
                List.of(new OptionSpec("date", "day", OptionType.DATE, true, null)),
                (args, ctx) -> { });

        assertEquals(List.of("date", "data", "help"),
                add.allOptions().stream().map(OptionSpec::name).toList());
    }
}
