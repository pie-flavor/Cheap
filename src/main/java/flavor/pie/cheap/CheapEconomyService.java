package flavor.pie.cheap;

import static com.google.common.base.Preconditions.checkNotNull;
import static flavor.pie.cheap.Cheap.MONEY;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CheapEconomyService implements EconomyService {

    static DataQuery query = DataQuery.of("cheap");
    Currency defaultCurrency;
    Map<String, Account> vcache = Maps.newHashMap();
    Map<UUID, UniqueAccount> cache = Maps.newHashMap();

    CheapEconomyService(Currency currency) {
        this.defaultCurrency = currency;
    }

    @Override
    public Currency getDefaultCurrency() {
        return defaultCurrency;
    }

    @Override
    public Set<Currency> getCurrencies() {
        return ImmutableSet.copyOf(Sponge.getRegistry().getAllOf(Currency.class));
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        checkNotNull(uuid, "uuid");
        UserStorageService svc = Sponge.getServiceManager().provideUnchecked(UserStorageService.class);
        Optional<User> user_ = svc.get(uuid);
        return user_.isPresent() && user_.get().get(CheapEconomyData.class).isPresent();
    }

    @Override
    public boolean hasAccount(String identifier) {
        WorldProperties world;
        if (checkNotNull(identifier, "identifier").contains("::")) {
            String[] split = identifier.split("::");
            String worldname = split[0];
            identifier = split[1];
            Optional<WorldProperties> world_ = Sponge.getServer().getWorldProperties(worldname);
            if (!world_.isPresent()) {
                return false;
            } else {
                world = world_.get();
            }
        } else {
            Optional<WorldProperties> world_ = Sponge.getServer().getDefaultWorld();
            if (!world_.isPresent()) {
                return false;
            }
            world = world_.get();
        }
        if (vcache.containsKey(world.getWorldName()+"::"+identifier)) return true;
        DataView view = world.getPropertySection(query).orElse(new MemoryDataContainer());
        return view.contains(DataQuery.of(identifier).then(MONEY.getQuery()));
    }

    @Override
    public Optional<UniqueAccount> getOrCreateAccount(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return Optional.ofNullable(cache.get(uuid));
        } else {
            Optional<UniqueAccount> acc_ = Sponge.getServiceManager().provideUnchecked(UserStorageService.class).get(checkNotNull(uuid, "uuid")).map(CheapUniqueAccount::new);
            acc_.ifPresent(acc -> cache.put(uuid, acc));
            return acc_;
        }
    }

    @Override
    public Optional<Account> getOrCreateAccount(String identifier) {
        WorldProperties props;
        if (checkNotNull(identifier).contains("::")) {
            String[] split = identifier.split("::");
            identifier = split[1];
            Optional<WorldProperties> props_ = Sponge.getServer().getWorldProperties(split[0]);
            if (props_.isPresent()) {
                props = props_.get();
            } else {
                return Optional.empty();
            }
        } else {
            Optional<WorldProperties> props_ = Sponge.getServer().getDefaultWorld();
            if (props_.isPresent()) {
                props = props_.get();
            } else {
                return Optional.empty();
            }
        }
        if (vcache.containsKey(props.getWorldName()+"::"+identifier)) {
            return Optional.ofNullable(vcache.get(props.getWorldName()+"::"+identifier));
        } else {
            Account acc = new CheapVirtualAccount(props, identifier);
            vcache.put(props.getWorldName()+"::"+identifier, acc);
            return Optional.of(acc);
        }
    }

    @Override
    public void registerContextCalculator(ContextCalculator<Account> calculator) {

    }


}
