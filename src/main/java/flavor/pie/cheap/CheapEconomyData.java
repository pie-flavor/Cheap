package flavor.pie.cheap;

import static com.google.common.base.Preconditions.checkNotNull;
import static flavor.pie.cheap.Cheap.MONEY;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.manipulator.DataManipulatorBuilder;
import org.spongepowered.api.data.manipulator.immutable.common.AbstractImmutableMappedData;
import org.spongepowered.api.data.manipulator.mutable.common.AbstractMappedData;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.persistence.AbstractDataBuilder;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.service.economy.Currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class CheapEconomyData extends AbstractMappedData<Currency, BigDecimal, CheapEconomyData, CheapEconomyData.Immutable> {

    Map<Currency, BigDecimal> map;

    protected CheapEconomyData(Map<Currency, BigDecimal> value) {
        super(value, MONEY);
        map = Maps.newHashMap(value);
    }

    static Map<String, Object> export(Map<Currency, BigDecimal> in) {
        Map<String, Object> map = Maps.newHashMap();
        in.forEach((c, d) -> map.put(c.getId(), d.doubleValue()));
        return map;
    }

    static Map<Currency, BigDecimal> inport(Map<?, ?> in) {
        Map<Currency, BigDecimal> map = Maps.newHashMap();
        in.forEach((o, o2) -> map.put(Sponge.getRegistry().getType(Currency.class, o.toString()).orElseThrow(null), BigDecimal.valueOf((Double) o2)));
        map.remove(null);
        return map;
    }

    @Override
    public Optional<BigDecimal> get(Currency key) {
        return Optional.of(map.getOrDefault(checkNotNull(key, "key"), BigDecimal.ZERO));
    }

    @Override
    public Set<Currency> getMapKeys() {
        return ImmutableSet.copyOf(map.keySet());
    }

    @Override
    public CheapEconomyData put(Currency key, BigDecimal value) {
        map.put(checkNotNull(key, "key"), checkNotNull(value, "value"));
        return this;
    }

    @Override
    public CheapEconomyData putAll(Map<? extends Currency, ? extends BigDecimal> map) {
        this.map.putAll(checkNotNull(map, "map"));
        return this;
    }

    @Override
    public CheapEconomyData remove(Currency key) {
        map.remove(checkNotNull(key, "key"));
        return this;
    }

    @Override
    public Optional<CheapEconomyData> fill(DataHolder dataHolder, MergeFunction overlap) {
        checkNotNull(dataHolder, "dataHolder").get(CheapEconomyData.class).ifPresent(d -> map.putAll(d.map));
        return Optional.of(this);
    }

    @Override
    public Optional<CheapEconomyData> from(DataContainer container) {
        try {
            checkNotNull(container, "container").getMap(MONEY.getQuery()).ifPresent(m -> map = inport(m));
        } catch (NullPointerException ignored) {
        }
        return Optional.of(this);
    }

    @Override
    public CheapEconomyData copy() {
        return new CheapEconomyData(map);
    }

    @Override
    public Immutable asImmutable() {
        return new CheapEconomyData.Immutable(map);
    }

    @Override
    public int getContentVersion() {
        return 1;
    }

    static class Immutable extends AbstractImmutableMappedData<Currency, BigDecimal, Immutable, CheapEconomyData> {

        Map<Currency, BigDecimal> map;

        protected Immutable(Map<Currency, BigDecimal> value) {
            super(value, MONEY);
            map = ImmutableMap.copyOf(value);
        }

        @Override
        public CheapEconomyData asMutable() {
            return new CheapEconomyData(map);
        }

        @Override
        public int getContentVersion() {
            return 1;
        }
    }

    static class Builder extends AbstractDataBuilder<CheapEconomyData>
            implements DataManipulatorBuilder<CheapEconomyData, CheapEconomyData.Immutable> {

        public Builder() {
            super(CheapEconomyData.class, 1);
        }

        @Override
        public CheapEconomyData create() {
            return new CheapEconomyData(ImmutableMap.of());
        }

        @Override
        public Optional<CheapEconomyData> createFrom(DataHolder dataHolder) {
            return Optional.of(dataHolder.get(CheapEconomyData.class).orElse(create()));
        }

        @Override
        protected Optional<CheapEconomyData> buildContent(DataView container) throws InvalidDataException {
            return Optional.of(new CheapEconomyData(container.getMap(MONEY.getQuery()).map(CheapEconomyData::inport).orElse(ImmutableMap.of())));
        }
    }
}
