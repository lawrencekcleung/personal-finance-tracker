package io.github.lawrencekcleung.pft.cli;

import io.github.lawrencekcleung.pft.model.Money;
import picocli.CommandLine;

/**
 * Converts {@code --amount} text into {@link Money} using the domain parser, so the CLI accepts
 * exactly the same syntax and rejects exactly the same values as the rest of the application.
 */
public final class MoneyConverter implements CommandLine.ITypeConverter<Money> {

    @Override
    public Money convert(String value) {
        return Money.parse(value);
    }
}
