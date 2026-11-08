package io.github.lawrencekcleung.pft.cli;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The command table. Dispatch is a map lookup, so adding a command never touches
 * {@link Cli} or {@link io.github.lawrencekcleung.pft.Main}.
 */
public final class CommandRegistry {

    private final Map<String, CommandDefinition> commands;

    /**
     * Builds a registry from the given definitions, preserving registration order for help.
     *
     * @param definitions the commands to register
     * @throws IllegalArgumentException if two definitions share a name
     */
    public CommandRegistry(List<CommandDefinition> definitions) {
        Map<String, CommandDefinition> byName = new LinkedHashMap<>();
        for (CommandDefinition definition : definitions) {
            if (byName.putIfAbsent(definition.name(), definition) != null) {
                throw new IllegalArgumentException("duplicate command: " + definition.name());
            }
        }
        this.commands = Collections.unmodifiableMap(byName);
    }

    /**
     * Looks up a command by name.
     *
     * @param name the command word
     * @return the definition, or empty when no such command exists
     */
    public Optional<CommandDefinition> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    /**
     * Returns every registered command in registration order.
     *
     * @return an unmodifiable view of the commands
     */
    public Collection<CommandDefinition> all() {
        return commands.values();
    }
}
