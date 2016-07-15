package flavor.pie.cheap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.VirtualAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionTypes;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.storage.WorldProperties;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static flavor.pie.cheap.CheapEconomyService.query;

public class CheapVirtualAccount implements VirtualAccount {
    WorldProperties world;
    String id;
    Map<Currency, BigDecimal> getMap() {
        return CheapEconomyData.inport(((Optional<Map<?, ?>>) world.getPropertySection(query).flatMap(v -> v.getMap(DataQuery.of(identifier)))).orElseGet(Maps::newHashMap));
    }
    String identifier;
    CheapVirtualAccount(WorldProperties props, String identifier) {
        world = props;
        id = identifier;
        this.identifier = identifier;
    }

    @Override
    public Text getDisplayName() {
        return Text.of(world.getWorldName()+"::"+id);
    }

    @Override
    public BigDecimal getDefaultBalance(Currency currency) {
        if (checkNotNull(currency) instanceof CheapCurrency) {
            return ((CheapCurrency) currency).getDefaultValue();
        } else {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean hasBalance(Currency currency, Set<Context> contexts) {
        return getBalance(currency, contexts).compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public BigDecimal getBalance(Currency currency, Set<Context> contexts) {
        return getMap().getOrDefault(checkNotNull(currency, "currency"), getDefaultBalance(currency));
    }

    @Override
    public Map<Currency, BigDecimal> getBalances(Set<Context> contexts) {
        return ImmutableMap.copyOf(getMap());
    }

    @Override
    public TransactionResult setBalance(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        Map<Currency, BigDecimal> map = getMap();
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        map.put(checkNotNull(currency, "currency"), amount);
        return new CheapTransactionResult(this, currency, amount, checkNotNull(contexts, "contexts"), ResultType.SUCCESS, TransactionTypes.DEPOSIT);
    }

    @Override
    public Map<Currency, TransactionResult> resetBalances(Cause cause, Set<Context> contexts) {
        Map<Currency, BigDecimal> original = getMap();
        update(ImmutableMap.of());
        ImmutableMap.Builder<Currency, TransactionResult> builder = ImmutableMap.builder();
        original.forEach((currency, amount) -> builder.put(currency, new CheapTransactionResult(this, currency, amount.subtract(getDefaultBalance(currency)), checkNotNull(contexts, "contexts"), ResultType.SUCCESS, TransactionTypes.WITHDRAW)));
        return builder.build();
    }

    @Override
    public TransactionResult resetBalance(Currency currency, Cause cause, Set<Context> contexts) {
        Map<Currency, BigDecimal> map = getMap();
        BigDecimal amount = map.remove(checkNotNull(currency, "currency"));
        update(map);
        return new CheapTransactionResult(this, currency, amount.subtract(getDefaultBalance(currency)), checkNotNull(contexts, "contexts"), ResultType.SUCCESS, TransactionTypes.WITHDRAW);
    }

    @Override
    public TransactionResult deposit(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        Map<Currency, BigDecimal> map = getMap();
        map.put(checkNotNull(currency, "currency"), map.getOrDefault(currency, getDefaultBalance(currency)).add(amount));
        update(map);
        return new CheapTransactionResult(this, currency, amount, checkNotNull(contexts, "contexts"), ResultType.SUCCESS, TransactionTypes.DEPOSIT);
    }

    @Override
    public TransactionResult withdraw(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        Map<Currency, BigDecimal> map = getMap();
        BigDecimal original = map.getOrDefault(checkNotNull(currency, "currency"), getDefaultBalance(currency));
        if (original.compareTo(amount) < 0) {
            return new CheapTransactionResult(this, currency, amount, checkNotNull(contexts, "contexts"), ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW);
        }
        BigDecimal current = original.subtract(amount);
        map.put(currency, amount);
        update(map);
        return new CheapTransactionResult(this, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.WITHDRAW);
    }

    @Override
    public TransferResult transfer(Account to, Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        checkNotNull(to, "to");
        TransactionResult result = withdraw(checkNotNull(currency, "currency"), amount, checkNotNull(cause, "cause"), checkNotNull(contexts, "contexts"));
        if (!result.getResult().equals(ResultType.SUCCESS)) {
            return new CheapTransferResult(this, to, currency, amount, contexts, result.getResult(), TransactionTypes.TRANSFER);
        }
        TransactionResult otherResult = to.deposit(currency, amount, cause, contexts);
        if (!result.getResult().equals(ResultType.SUCCESS)) {
            deposit(currency, amount, cause, contexts);
            return new CheapTransferResult(this, to, currency, amount, contexts, otherResult.getResult(), TransactionTypes.TRANSFER);
        }
        return new CheapTransferResult(this, to, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.TRANSFER);
    }

    @Override
    public String getIdentifier() {
        return world.getWorldName()+"::"+identifier;
    }

    @Override
    public Set<Context> getActiveContexts() {
        return ImmutableSet.of();
    }

    void update(Map<Currency, BigDecimal> map) {
        DataView container = world.getPropertySection(query).orElse(new MemoryDataContainer());
        container.set(DataQuery.of(identifier), CheapEconomyData.export(map));
        world.setPropertySection(query, container);
    }

    void doNothing() {
        update(getMap());
    }
}
