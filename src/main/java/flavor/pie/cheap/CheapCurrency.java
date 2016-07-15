package flavor.pie.cheap;

import com.google.common.collect.ImmutableMap;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;

import java.math.BigDecimal;
import java.math.MathContext;

import static com.google.common.base.Preconditions.checkNotNull;

public class CheapCurrency implements Currency {

    Text displayName;
    Text pluralName;
    Text symbol;
    TextTemplate format;
    int digits;
    boolean isDefault;
    String id;
    BigDecimal defaultValue;

    CheapCurrency(Text displayName, Text pluralName, Text symbol, TextTemplate format, int digits, boolean isDefault, String id,
            BigDecimal defaultValue) {
        this.displayName = displayName;
        this.pluralName = pluralName;
        this.symbol = symbol;
        this.format = format;
        this.digits = digits;
        this.isDefault = isDefault;
        this.id = id;
        this.defaultValue = defaultValue;
    }

    @Override
    public Text getDisplayName() {
        return displayName;
    }

    @Override
    public Text getPluralDisplayName() {
        return pluralName;
    }

    @Override
    public Text getSymbol() {
        return symbol;
    }

    @Override
    public Text format(BigDecimal amount, int numFractionDigits) {
        return format.apply(ImmutableMap.of("symbol", symbol, "amount", checkNotNull(amount, "amount").round(new MathContext(checkNotNull(numFractionDigits, "numFractionDigits"))).toPlainString())).build();
    }

    @Override
    public int getDefaultFractionDigits() {
        return digits;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return displayName.toPlain();
    }

    public BigDecimal getDefaultValue() {
        return defaultValue;
    }
}
