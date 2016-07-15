package flavor.pie.cheap;

import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.Set;

public class CheapTransactionResult implements TransactionResult {
    Account account;
    Currency currency;
    BigDecimal amount;
    Set<Context> contexts;
    ResultType result;
    TransactionType type;

    CheapTransactionResult(Account account, Currency currency, BigDecimal amount, Set<Context> contexts, ResultType result, TransactionType type) {
        this.account = account;
        this.currency = currency;
        this.amount = amount;
        this.contexts = contexts;
        this.result = result;
        this.type = type;
    }

    @Override
    public Account getAccount() {
        return null;
    }

    @Override
    public Currency getCurrency() {
        return null;
    }

    @Override
    public BigDecimal getAmount() {
        return null;
    }

    @Override
    public Set<Context> getContexts() {
        return null;
    }

    @Override
    public ResultType getResult() {
        return null;
    }

    @Override
    public TransactionType getType() {
        return null;
    }
}
