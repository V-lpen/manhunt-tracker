package cn.noryea.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.TeamArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public class ManhuntCommand {
  private static final ManhuntConfig config = ManhuntConfig.INSTANCE;

  @SuppressWarnings("unused")
  public static void registerCommands(CommandDispatcher<ServerCommandSource> dis, CommandRegistryAccess reg, CommandManager.RegistrationEnvironment env) {

    dis.register(CommandManager.literal("mh")
        .then(CommandManager.literal("join")
            .then(CommandManager.argument("team", TeamArgumentType.team())
                .executes((ctx) -> executeJoin(ctx.getSource(), TeamArgumentType.getTeam(ctx, "team")))))
        .then(CommandManager.literal("cure").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("targets", EntityArgumentType.players())
                .executes((ctx) -> executeCure(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets")))))
        .then(CommandManager.literal("freeze").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 120))
                .executes((ctx) -> executeFreeze(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
        .then(CommandManager.literal("compassDelay").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 120))
                .executes((ctx) -> executeCompassDelay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
        .then(CommandManager.literal("runnersWinOnDragonDeath").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("boolean", BoolArgumentType.bool())
                .executes((ctx) -> setRunnersWinOnDragonDeath(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
        .then(CommandManager.literal("setColor").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("team", TeamArgumentType.team())
                .then(CommandManager.argument("color", ColorArgumentType.color())
                    .executes((ctx) -> executeChangeTeamColor(ctx.getSource(), TeamArgumentType.getTeam(ctx, "team"), ColorArgumentType.getColor(ctx, "color"))))))
        .then(CommandManager.literal("showActionBar").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("boolean", BoolArgumentType.bool())
                .executes((ctx) -> executeShowActionBar(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
        .then(CommandManager.literal("showRunnerDimension").requires((src) -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("boolean", BoolArgumentType.bool())
                .executes((ctx) -> executeShowRunnerDimension(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
        .then(CommandManager.literal("reload").requires((src) -> src.hasPermissionLevel(2))
            .executes((ctx) -> executeReload(ctx.getSource())))
    );
  }

  private static int executeJoin(ServerCommandSource source, Team team) {
    Scoreboard scoreboard = source.getServer().getScoreboard();

    scoreboard.addPlayerToTeam(source.getPlayer().getName().getString(), team);
    source.sendFeedback(Text.of(String.format("Added %1$s to team %2$s", source.getPlayer().getName().getString(), team.getFormattedName().getString())), true); //Text.translatable("commands.team.join.success.single", source.getPlayer().getName(), team.getFormattedName())

    return 1;
  }

  private static int executeCompassDelay(ServerCommandSource source, Integer delay) {
    config.setDelay(delay);
    source.sendFeedback(Text.of(String.format("§7Set delay to: §f%d §7seconds", delay)), true); //Text.translatable("manhunt.commands.delay", delay)

    return 1;
  }

  private static int setRunnersWinOnDragonDeath(ServerCommandSource source, boolean bool) {
    config.setRunnersWinOnDragonDeath(bool);
    source.sendFeedback(Text.of(String.format("§7Set runnersWinOnDragonDeath to: §f%s", bool)), true); //Text.translatable("manhunt.commands.runnerswinset", bool)

    return 1;
  }

  private static int executeCure(ServerCommandSource source, Collection<? extends Entity> targets) {
    for (Entity target : targets) {
      ServerPlayerEntity player = (ServerPlayerEntity) target;

      player.clearStatusEffects();
      player.setHealth(player.getMaxHealth());
      player.getHungerManager().setFoodLevel(20);
      player.getHungerManager().setSaturationLevel(8.5F);

    }
    source.sendFeedback(Text.of(String.format("§7Cured §f%d §7targets", targets.size())), true); //Text.translatable("manhunt.commands.cured", targets.size())
    return targets.size();
  }

  private static int executeFreeze(ServerCommandSource source, int time) throws CommandSyntaxException {
    MinecraftServer server = source.getEntityOrThrow().getServer();

    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

      if (player.isTeamPlayer(server.getScoreboard().getTeam("hunters"))) {

        player.clearStatusEffects();
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(8.5F);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, time * 20, 255, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, time * 20, 255, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, time * 20, 248, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, (time - 1) * 20, 255, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, time * 20, 255, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, time * 20, 255, false, false));
      }
    }

    source.sendFeedback(Text.of(String.format("§7Hunters will be frozen for §f%d§7 seconds", time)), true); //Text.translatable("manhunt.commands.freeze", time)

    return 1;
  }

  private static int executeChangeTeamColor(ServerCommandSource source, Team team, Formatting color) {
    if(team.getName().equals("hunters")) { config.setHuntersColor(color); }
    else if(team.getName().equals("runners")) { config.setRunnersColor(color); }
    else {
      source.sendFeedback(Text.of(String.format("§cYou can only change colors of §f%1$s §cand §f%2$s §cteams", "Hunters", "Runners")), true);
      return -1;
    } //Text.translatable("manhunt.commands.teamColor.badTeam", Text.translatable("manhunt.teams.hunters.name"), Text.translatable("manhunt.teams.runners.name")

    team.setColor(color);
    source.sendFeedback(Text.of(String.format("§7Changed color of §f%1$s §7team to §f%2$s", Text.of(team.getName()), color.getName())), true);
    return 1;
  } //Text.translatable("manhunt.commands.teamColor.success", Text.translatable("manhunt.teams." + team.getName() + ".name"), color.getName())

  private static int executeShowActionBar(ServerCommandSource source, boolean bool) {
    config.setShowTitle(bool);
    source.sendFeedback(Text.of(String.format("§7Set showActionBar to: §f%s", bool)), true);
    return 1;
  }

  private static int executeShowRunnerDimension(ServerCommandSource source, boolean bool) {
    config.setShowRunnerDimension(bool);
    source.sendFeedback(Text.of(String.format("§7Set showRunnerDimension to: §f%s", bool)), true);
    return 1;
  }

  private static int executeReload(ServerCommandSource source) {
    config.load();
    source.sendFeedback(Text.of("§7Succesfully reloaded configuration file"), true); //Text.translatable("manhunt.commands.reload")
    return 1;
  }
}
