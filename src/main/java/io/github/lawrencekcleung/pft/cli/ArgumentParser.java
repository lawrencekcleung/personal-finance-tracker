package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.error.UsageException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns command-line tokens into a {@link ParsedArgs}, guided by declared {@link OptionSpec}s.
 *
 * <p>Both {@code --name value} and {@code --name=value} are accepted. Values are converted as
 * soon as they are read, so the first bad option decides the error. Parsing is deliberately an
 * index-based scan rather than a stream pipeline: the grammar is inherently positional.</p>
 */
public final class ArgumentParser {

    /**
     * Parses {@code tokens} against {@code specs}.
     *
     * @param specs  every option the command accepts, globals included
     * @param tokens the tokens after the command name
     * @return the typed arguments
     * @throws UsageException if an option is unknown, repeated, missing its value, has a value it
     *                        cannot parse, or a required option is absent
     */
    public ParsedArgs parse(List<OptionSpec> specs, List<String> tokens) {
        Map<String, OptionSpec> byName = new LinkedHashMap<>();
        for (OptionSpec spec : specs) {
            if (byName.putIfAbsent(spec.name(), spec) != null) {
                throw new IllegalArgumentException("duplicate option --" + spec.name());
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!token.startsWith("--") || token.length() == 2) {
                throw new UsageException("unexpected argument '" + token + "'");
            }
            String body = token.substring(2);
            int equals = body.indexOf('=');
            String name = equals < 0 ? body : body.substring(0, equals);
            String inlineValue = equals < 0 ? null : body.substring(equals + 1);

            OptionSpec spec = byName.get(name);
            if (spec == null) {
                throw new UsageException("unknown option --" + name
                        + "; valid options: " + validNames(byName));
            }
            if (values.containsKey(name)) {
                throw new UsageException("--" + name + ": specified more than once");
            }
            if (spec.type() == OptionType.FLAG) {
                if (inlineValue != null) {
                    throw new UsageException("--" + name + ": flag takes no value");
                }
                values.put(name, Boolean.TRUE);
                continue;
            }

            String raw = inlineValue;
            if (raw == null) {
                if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--")) {
                    throw new UsageException("--" + name + ": missing value");
                }
                raw = tokens.get(++i);
            }
            values.put(name, spec.type().convert(name, raw));
        }

        for (OptionSpec spec : specs) {
            if (values.containsKey(spec.name())) {
                continue;
            }
            if (spec.hasDefault()) {
                values.put(spec.name(), spec.type().convert(spec.name(), spec.defaultValue()));
            } else if (spec.required()) {
                throw new UsageException("missing required option --" + spec.name());
            }
        }

        Map<String, OptionType> types = new LinkedHashMap<>();
        for (OptionSpec spec : specs) {
            types.put(spec.name(), spec.type());
        }
        return new ParsedArgs(values, types);
    }

    private static String validNames(Map<String, OptionSpec> byName) {
        if (byName.isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>(byName.size());
        for (String name : byName.keySet()) {
            names.add("--" + name);
        }
        return String.join(", ", names);
    }
}
