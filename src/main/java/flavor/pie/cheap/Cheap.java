package flavor.pie.cheap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.KeyFactory;
import org.spongepowered.api.data.value.mutable.MapValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static flavor.pie.cheap.CheapEconomyService.query;
import static flavor.pie.util.arguments.MoreArguments.bigInteger;
import static org.spongepowered.api.command.args.GenericArguments.*;

@Plugin(id = "cheap", name = "Cheap", version = "0.1", authors = "pie_flavor", description = "An economy plugin implemented with NBT.")
public class Cheap {

    public static Key<MapValue<Currency, BigDecimal>> MONEY;
    @Inject
    Game game;
    @Inject
    PluginContainer container;
    @Inject
    Logger logger;
    @Inject @ConfigDir(sharedRoot = false)
    Path dir;
    CheapEconomyData.Builder builder;
    CheapEconomyService service;
    ConfigurationNode root;
    CheapCurrencyRegistryModule module;
    boolean needsRemapping = false;

    @Listener
    public void preInit(GamePreInitializationEvent e) throws IOException, ObjectMappingException {
        if (MONEY == null) {
            MONEY = KeyFactory.makeMapKey(Currency.class, BigDecimal.class, DataQuery.of("money"));
        }
        loadConfig();
        Set<Currency> set = loadCurrencies();
        module = new CheapCurrencyRegistryModule(set);
        game.getRegistry().registerModule(Currency.class, module);
        loadRemappings();
        service = new CheapEconomyService(Iterables.getOnlyElement(set.stream().filter(Currency::isDefault).collect(Collectors.toList())));
        builder = new CheapEconomyData.Builder();
        game.getDataManager().register(CheapEconomyData.class, CheapEconomyData.Immutable.class, builder);
        game.getServiceManager().setProvider(this, EconomyService.class, service);
    }

    @Listener
    public void init(GameInitializationEvent e) {
        CommandSpec bal = CommandSpec.builder()
                .executor(this::bal)
                .arguments(
                        userOrSource(Text.of("user")),
                        optional(catalogedElement(Text.of("currency"), Currency.class))
                ).build();
        CommandSpec vbal = CommandSpec.builder()
                .executor(this::vbal)
                .permission("cheap.bal.other.virtual")
                .arguments(
                        string(Text.of("account")),
                        optional(catalogedElement(Text.of("currency"), Currency.class))
                ).build();
        CommandSpec pay = CommandSpec.builder()
                .executor(this::pay)
                .arguments(
                        user(Text.of("to")),
                        optional(catalogedElement(Text.of("currency"), Currency.class)),
                        bigInteger(Text.of("amount"))
                ).build();
        CommandSpec vpay = CommandSpec.builder()
                .executor(this::vpay)
                .arguments(
                        string(Text.of("account")),
                        optional(catalogedElement(Text.of("currency"), Currency.class)),
                        bigInteger(Text.of("amount"))
                ).build();
        CommandSpec vset = CommandSpec.builder()
                .executor(this::vset)
                .permission("cheap.set.virtual")
                .arguments(
                        string(Text.of("account")),
                        optional(catalogedElement(Text.of("currency"), Currency.class)),
                        bigInteger(Text.of("amount"))
                ).build();
        CommandSpec set = CommandSpec.builder()
                .executor(this::vset)
                .permission("cheap.set.player")
                .arguments(
                        userOrSource(Text.of("user")),
                        optional(catalogedElement(Text.of("currency"), Currency.class)),
                        bigInteger(Text.of("amount"))
                ).build();
        CommandSpec vcreate = CommandSpec.builder()
                .executor(this::vcreate)
                .permission("cheap.set.create.virtual")
                .arguments(
                        string(Text.of("account"))
                ).build();
        CommandManager mgr = game.getCommandManager();
        mgr.register(this, bal, "balance", "bal");
        mgr.register(this, vbal, "vbalance", "vbal");
        mgr.register(this, pay, "pay");
        mgr.register(this, vpay, "vpay");
        mgr.register(this, set, "setbalance", "setbal");
        mgr.register(this, vset, "vsetbalance", "vsetbal");
        mgr.register(this, vcreate, "vcreate");
    }

    @Listener
    public void started(GameStartedServerEvent e) {
        if (needsRemapping) {
            logger.info("Remappings detected.");
            logger.info("Forcing a reload of all additional world data. This could take some time depending on the server size.");
            game.getServer().getAllWorldProperties().forEach(props -> props.getAdditionalProperties().getMap(query).ifPresent(map -> map.forEach((key, value) -> ((CheapVirtualAccount) service.getOrCreateAccount(props.getWorldName()+"::"+key).get()).doNothing())));
            logger.info("Forcing a reload of all players. This could take some time depending on the server size.");
            UserStorageService svc = game.getServiceManager().provideUnchecked(UserStorageService.class);
            svc.getAll().forEach(prof -> svc.get(prof).ifPresent(user -> user.getOrCreate(CheapEconomyData.class).get().map.remove(null)));
            logger.info("Remap complete.");
        }
    }

    CommandResult bal(CommandSource src, CommandContext args) throws CommandException {
        User user = args.<User>getOne("user").get();
        if (user != src) {
            args.checkPermission(src, "cheap.bal.other.player");
        }
        UniqueAccount acc = service.getOrCreateAccount(user.getUniqueId()).get();
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        BigDecimal balance = acc.getBalance(currency);
        src.sendMessage(Text.of((src == user ? "Your" : "Their")+ " balance: ", currency.format(balance)));
        return CommandResult.builder().queryResult(toInt(balance)).build();
    }

    CommandResult vbal(CommandSource src, CommandContext args) throws CommandException {
        String name = args.<String>getOne("account").get();
        if (!service.hasAccount(name)) {
            throw new CommandException(Text.of("The account does not exist!"));
        }
        Account account = service.getOrCreateAccount(name).get();
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        BigDecimal balance = account.getBalance(currency);
        src.sendMessage(Text.of("Account balance: ", currency.format(balance)));
        return CommandResult.builder().queryResult(toInt(balance)).build();
    }

    CommandResult pay(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of("Only players can pay other players!"));
        }
        User to = args.<User>getOne("to").get();
        User from = (User) src;
        Account toAcc = service.getOrCreateAccount(to.getUniqueId()).get();
        Account fromAcc = service.getOrCreateAccount(from.getUniqueId()).get();
        BigDecimal bal = args.<BigDecimal>getOne("amount").get();
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        TransactionResult result = fromAcc.transfer(toAcc, currency, bal, Cause.of(NamedCause.source(src), NamedCause.of("Plugin", container)), ImmutableSet.of());
        if (result.getResult().equals(ResultType.SUCCESS)) {
            src.sendMessage(Text.of("Paid ", currency.format(bal), " to "+to.getName()));
            if (to.isOnline()) to.getPlayer().get().sendMessage(Text.of(src.getName()+" paid you ", currency.format(bal)));
            return CommandResult.builder().queryResult(toInt(bal)).build();
        } else {
            throw new CommandException(Text.of("You don't have enough money!"));
        }
    }

    CommandResult vpay(CommandSource src, CommandContext args) throws CommandException{
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of("Only players can pay virtual accounts!"));
        }
        User from = (User) src;
        String name = args.<String>getOne("account").get();
        if (!service.hasAccount(name)) {
            throw new CommandException(Text.of("The account does not exist!"));
        }
        Account fromAcc = service.getOrCreateAccount(from.getUniqueId()).get();
        Account toAcc = service.getOrCreateAccount(name).get();
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        BigDecimal amount = args.<BigDecimal>getOne("amount").get();
        TransactionResult result = fromAcc.transfer(toAcc, currency, amount, Cause.of(NamedCause.source(src), NamedCause.of("Plugin", container)));
        if (result.getResult().equals(ResultType.SUCCESS)) {
            src.sendMessage(Text.of("Transferred ", currency.format(amount), " to the account "+toAcc.getIdentifier()));
            return CommandResult.builder().queryResult(toInt(amount)).build();
        } else {
            throw new CommandException(Text.of("You don't have enough money!"));
        }
    }

    CommandResult set(CommandSource src, CommandContext args) throws CommandException {
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        BigDecimal amount = args.<BigDecimal>getOne("amount").get();
        User user = args.<User>getOne("user").get();
        service.getOrCreateAccount(user.getUniqueId()).get().setBalance(currency, amount, Cause.of(NamedCause.source(src), NamedCause.of("Plugin", container)));
        src.sendMessage(Text.of("Set "+user.getName()+"'s balance to ", currency.format(amount)));
        return CommandResult.builder().queryResult(toInt(amount)).build();
    }

    CommandResult vset(CommandSource src, CommandContext args) throws CommandException {
        String accname = args.<String>getOne("account").get();
        if (!service.hasAccount(accname)) {
            throw new CommandException(Text.of("This account does not exist!"));
        }
        Account acc = service.getOrCreateAccount(accname).get();
        Currency currency = args.<Currency>getOne("currency").orElse(service.getDefaultCurrency());
        BigDecimal amount = args.<BigDecimal>getOne("amount").get();
        acc.setBalance(currency, amount, Cause.of(NamedCause.source(src), NamedCause.of("Plugin", container)));
        src.sendMessage(Text.of("Set the balance of "+accname+" to ", currency.format(amount)));
        return CommandResult.builder().queryResult(toInt(amount)).build();
    }

    CommandResult vcreate(CommandSource src, CommandContext args) throws CommandException {
        String accname = args.<String>getOne("account").get();
        Cause cause = Cause.of(NamedCause.source(src), NamedCause.of("Plugin", container));
        Account acc = service.getOrCreateAccount(accname).get();
        acc.resetBalances(cause);
        acc.resetBalance(service.getDefaultCurrency(), cause);
        src.sendMessage(Text.of("Created a new virtual account."));
        return CommandResult.success();
    }

    void loadConfig() throws IOException {
        if (Files.notExists(dir)) {
            try {
                Files.createDirectory(dir);
            } catch (IOException | SecurityException ex) {
                logger.error("Could not create the config directory!");
                disable();
                throw ex;
            }
        }
        Path configFile = dir.resolve("currencies.conf");
        if (Files.notExists(configFile)) {
            try {
                container.getAsset("default.conf").get().copyToFile(configFile);
            } catch (IOException | SecurityException ex) {
                logger.error("Could not create the config file!");
                disable();
                throw ex;
            }
        }
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setPath(configFile).build();
        root = loader.load();
    }

    Set<Currency> loadCurrencies() throws ObjectMappingException {
        Set<Currency> currencies = Sets.newHashSet();
        String defaultCurrency = root.getNode("default-currency").getString();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.getNode("currencies").getChildrenMap().entrySet()) {
            if (defaultCurrency == null) defaultCurrency = entry.getKey().toString();
            try {
                currencies.add(parse(entry.getKey().toString(), entry.getValue(), defaultCurrency));
            } catch (ObjectMappingException ex) {
                logger.error("Could not correctly parse the config!");
                disable();
                throw ex;
            }
        }
        return currencies;
    }

    void loadRemappings() {
        ConfigurationNode node = root.getNode("remap");
        if (node.hasMapChildren()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.getChildrenMap().entrySet()) {
                module.setRemapping(entry.getKey().toString(), entry.getValue().getString());
                if (!needsRemapping) needsRemapping = true;
            }
        }
    }

    Currency parse(String name, ConfigurationNode node, String defaultCurrency) throws ObjectMappingException {
        TypeToken<Text> textToken = TypeToken.of(Text.class);
        Text displayName = node.getNode("name").getValue(textToken);
        Text plural = node.getNode("plural").getValue(textToken, displayName.toBuilder().append(Text.of("s")).build());
        Text symbol = node.getNode("symbol").getValue(textToken);
        TextTemplate format = node.getNode("format").getValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextTemplate.arg("symbol").build(), TextTemplate.arg("amount").build()));
        int digits = node.getNode("digits").getInt(2);
        double value = node.getNode("default-value").getDouble(0);
        boolean isDefault = defaultCurrency.equals(name);
        return new CheapCurrency(displayName, plural, symbol, format, digits, isDefault, name, BigDecimal.valueOf(value));
    }

    void disable() {
        game.getEventManager().unregisterPluginListeners(this);
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
        logger.info("Cheap is now disabled.");
    }

    int toInt(BigDecimal source) {
        return source.compareTo(new BigDecimal(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : source.intValue();
    }
}
