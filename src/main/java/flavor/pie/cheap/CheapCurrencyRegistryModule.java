package flavor.pie.cheap;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.registry.AdditionalCatalogRegistryModule;
import org.spongepowered.api.registry.RegistrationPhase;
import org.spongepowered.api.registry.util.DelayedRegistration;
import org.spongepowered.api.service.economy.Currency;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class CheapCurrencyRegistryModule implements AdditionalCatalogRegistryModule<Currency> {

    Map<String, Currency> currencies;
    Set<Currency> defaults;
    Map<String, String> remap;

    CheapCurrencyRegistryModule(Set<Currency> defaults) {
        currencies = Maps.newHashMap();
        this.defaults = defaults;
        remap = Maps.newHashMap();
    }

    public void setRemapping(String previous, String current) {
        remap.put(previous, current);
    }

    @Override
    public void registerAdditionalCatalog(Currency extraCatalog) {
        currencies.put(checkNotNull(extraCatalog, "extraCatalog").getId(), extraCatalog);
    }

    @Override
    public Optional<Currency> getById(String id) {
        if (remap.containsKey(checkNotNull(id, "id"))) {
            id = remap.get(id);
        }
        return Optional.ofNullable(currencies.get(id));
    }

    @Override
    public Collection<Currency> getAll() {
        return ImmutableList.copyOf(currencies.values());
    }

    @Override
    @DelayedRegistration(RegistrationPhase.INIT)
    public void registerDefaults() {
        currencies.putAll(HashBiMap.create(Maps.asMap(defaults, Currency::getId)).inverse());
    }
}
