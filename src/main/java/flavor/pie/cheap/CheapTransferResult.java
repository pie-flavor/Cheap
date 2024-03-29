package flavor.pie.cheap;

import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionType;
import org.spongepowered.api.service.economy.transaction.TransferResult;

import java.math.BigDecimal;
import java.util.Set;

public class CheapTransferResult implements TransferResult {
    Account from;
    Account to;
    Currency currency;
    BigDecimal amount;
    Set<Context> contexts;
    ResultType result;
    TransactionType type;

    CheapTransferResult(Account from, Account to, Currency currency, BigDecimal amount, Set<Context> contexts, ResultType result, TransactionType type) {
        this.from = from;
        this.to = to;
        this.currency = currency;
        this.amount = amount;
        this.contexts = contexts;
        this.result = result;
        this.type = type;
    }

    @Override
    public Account getAccountTo() {
        return to;
    }

    @Override
    public Account getAccount() {
        return from;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Set<Context> getContexts() {
        return contexts;
    }

    @Override
    public ResultType getResult() {
        return result;
    }

    @Override
    public TransactionType getType() {
        return type;
    }
}
