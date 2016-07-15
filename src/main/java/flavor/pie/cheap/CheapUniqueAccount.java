package flavor.pie.cheap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionTypes;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CheapUniqueAccount implements UniqueAccount {

    User user;

    CheapUniqueAccount(User user) {
        this.user = user;
    }

    @Override
    public Text getDisplayName() {
        return Text.of(user.getName());
    }

    @Override
    public BigDecimal getDefaultBalance(Currency currency) {
        return checkNotNull(currency, "currency") instanceof CheapCurrency ? ((CheapCurrency) currency).getDefaultValue() : BigDecimal.ZERO;
    }

    @Override
    public boolean hasBalance(Currency currency, Set<Context> contexts) {
        return user.getOrCreate(CheapEconomyData.class).get().map.containsKey(checkNotNull(currency, "currency"));
    }

    @Override
    public BigDecimal getBalance(Currency currency, Set<Context> contexts) {
        return user.getOrCreate(CheapEconomyData.class).get().map.getOrDefault(checkNotNull(currency, "currency"), getDefaultBalance(currency));
    }

    @Override
    public Map<Currency, BigDecimal> getBalances(Set<Context> contexts) {
        return ImmutableMap.copyOf(user.getOrCreate(CheapEconomyData.class).get().map);
    }

    @Override
    public TransactionResult setBalance(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        checkNotNull(contexts, "contexts");
        user.getOrCreate(CheapEconomyData.class).get().map.put(checkNotNull(currency, "currency"), amount);
        return new CheapTransactionResult(this, currency, amount, checkNotNull(contexts, "contexts"), ResultType.SUCCESS, TransactionTypes.DEPOSIT);
    }

    @Override
    public Map<Currency, TransactionResult> resetBalances(Cause cause, Set<Context> contexts) {
        checkNotNull(contexts, "contexts");
        ImmutableMap.Builder<Currency, TransactionResult> builder = ImmutableMap.builder();
        for (Currency currency : getBalances().keySet()) {
            builder.put(currency, resetBalance(currency, cause, contexts));
        }
        return builder.build();
    }

    @Override
    public TransactionResult resetBalance(Currency currency, Cause cause, Set<Context> contexts) {
        checkNotNull(contexts, "contexts");
        BigDecimal amount = user.getOrCreate(CheapEconomyData.class).get().map.remove(checkNotNull(currency, "currency"));
        if (amount == null) amount = getDefaultBalance(currency);
        return new CheapTransactionResult(this, currency, amount.subtract(getDefaultBalance(currency)), contexts, ResultType.SUCCESS, TransactionTypes.WITHDRAW);
    }

    @Override
    public TransactionResult deposit(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        checkNotNull(contexts, "contexts");
        Map<Currency, BigDecimal> map = user.getOrCreate(CheapEconomyData.class).get().map;
        map.put(checkNotNull(currency, "currency"), map.getOrDefault(currency, getDefaultBalance(currency)).add(amount));
        return new CheapTransactionResult(this, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.DEPOSIT);
    }

    @Override
    public TransactionResult withdraw(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        checkNotNull(contexts, "contexts");
        checkArgument(checkNotNull(amount, "amount").compareTo(BigDecimal.ZERO) > 0, "amount must be greater than 0");
        Map<Currency, BigDecimal> map = user.getOrCreate(CheapEconomyData.class).get().map;
        BigDecimal original = map.getOrDefault(checkNotNull(currency, "currency"), getDefaultBalance(currency));
        if (original.compareTo(amount) < 0) {
            return new CheapTransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW);
        }
        map.put(currency, original.subtract(amount));
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
        TransactionResult otherResult = deposit(currency, amount, cause, contexts);
        if (!result.getResult().equals(ResultType.SUCCESS)) {
            deposit(currency, amount, cause, contexts);
            return new CheapTransferResult(this, to, currency, amount, contexts, otherResult.getResult(), TransactionTypes.TRANSFER);
        }
        return new CheapTransferResult(this, to, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.TRANSFER);
    }

    @Override
    public String getIdentifier() {
        return user.getName();
    }

    @Override
    public Set<Context> getActiveContexts() {
        return ImmutableSet.of();
    }

    @Override
    public UUID getUniqueId() {
        return user.getUniqueId();
    }
}
