package cn.noryea.manhunt;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;

public class ManhuntCommand {
  private static final ManhuntConfig config = ManhuntConfig.INSTANCE;

  @SuppressWarnings("unused")
  public static void registerCommands(CommandDispatcher<CommandSourceStack> dis, CommandBuildContext reg, Commands.CommandSelection env) {

    dis.register(Commands.literal("mh")
      .then(Commands.literal("join")
        .then(Commands.argument("team", TeamArgument.team())
          .executes((ctx) -> executeJoin(ctx.getSource(), TeamArgument.getTeam(ctx, "team")))))
      .then(Commands.literal("teamLimit")
        .then(Commands.argument("team", TeamArgument.team())
          .then(Commands.argument("limit", IntegerArgumentType.integer(-1, 255))
            .executes((ctx) -> executeTeamLimit(ctx.getSource(), TeamArgument.getTeam(ctx, "team"), IntegerArgumentType.getInteger(ctx, "limit"))))))
      .then(Commands.literal("clearTeams").requires((src) -> src.hasPermission(2)))
        .executes((ctx) -> executeClearTeams(ctx.getSource()))
      .then(Commands.literal("cure").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("targets", EntityArgument.players())
          .executes((ctx) -> executeCure(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
      .then(Commands.literal("freeze").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
          .executes((ctx) -> executeFreeze(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
      .then(Commands.literal("compassDelay").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 120))
          .executes((ctx) -> executeCompassDelay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
      .then(Commands.literal("automaticCompassUpdate").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("boolean", BoolArgumentType.bool())
          .executes((ctx) -> executeSetAutomaticCompassUpdate(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
      .then(Commands.literal("automaticCompassUpdateDelay").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
          .executes((ctx) -> executeSetAutomaticCompassUpdateDelay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
      .then(Commands.literal("runnersWinOnDragonDeath").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("boolean", BoolArgumentType.bool())
          .executes((ctx) -> setRunnersWinOnDragonDeath(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
      .then(Commands.literal("setColor").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("team", TeamArgument.team())
          .then(Commands.argument("color", ColorArgument.color())
            .executes((ctx) -> executeChangeTeamColor(ctx.getSource(), TeamArgument.getTeam(ctx, "team"), ColorArgument.getColor(ctx, "color"))))))
      .then(Commands.literal("showActionBar").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("boolean", BoolArgumentType.bool())
          .executes((ctx) -> executeShowActionBar(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
      .then(Commands.literal("showRunnerDimension").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("boolean", BoolArgumentType.bool())
          .executes((ctx) -> executeShowRunnerDimension(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
      .then(Commands.literal("friendlyfire").requires((src) -> src.hasPermission(2))
        .then(Commands.argument("boolean", BoolArgumentType.bool())
          .executes((ctx) -> executeFriendlyFire(ctx.getSource(), BoolArgumentType.getBool(ctx, "boolean")))))
      .then(Commands.literal("reload").requires((src) -> src.hasPermission(2))
        .executes((ctx) -> executeReload(ctx.getSource())))
    );
  }

  private static int executeJoin(CommandSourceStack source, PlayerTeam team) {
    Scoreboard scoreboard = source.getServer().getScoreboard();

    if(!team.getName().equals("hunters") && !team.getName().equals("runners")) {
      source.sendFailure(Component.translatable("manhunt.commands.join.wrongteam", Component.translatable("manhunt.teams.hunters.name"), Component.translatable("manhunt.teams.runners.name")));
      return -1;
    }

    if((team.getName().equals("hunters") && config.getHuntersLimit() >= 0 && team.getPlayers().size() >= config.getHuntersLimit()) ||
      (team.getName().equals("runners") && config.getRunnersLimit() >= 0 && team.getPlayers().size() >= config.getRunnersLimit())) {
      source.sendFailure(Component.translatable("manhunt.commands.join.teamlimit", Component.translatable("manhunt.teams." + team.getName() + ".name")));
      return -1;
    }

    scoreboard.addPlayerToTeam(source.getPlayer().getName().getString(), team);
    source.sendSuccess(() -> Component.translatable("commands.team.join.success.single", source.getPlayer().getName(), team.getFormattedDisplayName()), true);
    return 1;
  }

  private static int executeTeamLimit(CommandSourceStack source, PlayerTeam team, int limit) {
    if(!team.getName().equals("hunters") && !team.getName().equals("runners")) {
      source.sendFailure(Component.translatable("manhunt.commands.teamlimit.wrongteam", Component.translatable("manhunt.teams.hunters.name"), Component.translatable("manhunt.teams.runners.name")));
      return -1;
    }

    switch (team.getName()) {
      case "hunters" -> config.setHuntersLimit(limit);
      case "runners" -> config.setRunnersLimit(limit);
    }

    source.sendSuccess(() -> Component.translatable("manhunt.commands.teamlimit", Component.translatable("manhunt.teams." + team.getName() + ".name"), limit), true);
    return 1;
  }

  private static int executeClearTeams(CommandSourceStack source) {
    Scoreboard scoreboard = source.getServer().getScoreboard();
    PlayerTeam hunters = scoreboard.getPlayerTeam("hunters");
    PlayerTeam runners = scoreboard.getPlayerTeam("runners");

    if(hunters == null || runners == null) {
      source.sendFailure(Component.translatable("manhunt.commands.error.noteam"));
      return -1;
    }

    ArrayList<String> huntersPlayers = Lists.newArrayList(hunters.getPlayers());
    if(!huntersPlayers.isEmpty()) {
      for (String string : huntersPlayers) {
        scoreboard.removePlayerFromTeam(string, hunters);
        source.sendSuccess(() -> Component.translatable("commands.team.empty.success", huntersPlayers.size(), hunters.getFormattedDisplayName()), true);
      }
    }
    ArrayList<String> runnersPlayers = Lists.newArrayList(runners.getPlayers());
    if(!runnersPlayers.isEmpty()) {
      for (String string : runnersPlayers) {
        scoreboard.removePlayerFromTeam(string, hunters);
        source.sendSuccess(() -> Component.translatable("commands.team.empty.success", runnersPlayers.size(), runners.getFormattedDisplayName()), true);
      }
    }

    return 1;
  }

  private static int executeCompassDelay(CommandSourceStack source, Integer delay) {
    config.setDelay(delay);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.delay", delay), true);
    return 1;
  }

  private static int setRunnersWinOnDragonDeath(CommandSourceStack source, boolean bool) {
    config.setRunnersWinOnDragonDeath(bool);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.runnerswinset", bool), true);
    return 1;
  }

  private static int executeCure(CommandSourceStack source, Collection<? extends Entity> targets) {
    for (Entity target : targets) {
      ServerPlayer player = (ServerPlayer) target;

      player.removeAllEffects();
      player.setHealth(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(8.5F);

    }
    source.sendSuccess(() -> Component.translatable("manhunt.commands.cured", targets.size()), true);
    return targets.size();
  }

  private static int executeFreeze(CommandSourceStack source, int time) throws CommandSyntaxException {
    MinecraftServer server = source.getEntityOrException().getServer();

    for (ServerPlayer player : server.getPlayerList().getPlayers()) {

      if (player.isAlliedTo(server.getScoreboard().getPlayerTeam("hunters"))) {

        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(8.5F);

        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, time * 20, 255, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, time * 20, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, time * 20, 248, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, (time - 1) * 20, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, time * 20, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, time * 20, 255, false, false));
      }
    }

    source.sendSuccess(() -> Component.translatable("manhunt.commands.freeze", time), true);
    return 1;
  }

  private static int executeChangeTeamColor(CommandSourceStack source, PlayerTeam team, ChatFormatting color) {
    if (team.getName().equals("hunters")) {
      config.setHuntersColor(color);
    } else if (team.getName().equals("runners")) {
      config.setRunnersColor(color);
    } else {
      source.sendFailure(Component.translatable("manhunt.commands.teamcolor.badteam", Component.translatable("manhunt.teams.hunters.name"), Component.translatable("manhunt.teams.runners.name")));
      return -1;
    }

    team.setColor(color);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.teamcolor.success", Component.translatable("manhunt.teams." + team.getName() + ".name"), color.getName()), true);
    return 1;
  }

  private static int executeShowActionBar(CommandSourceStack source, boolean bool) {
    config.setShowTitle(bool);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.showactionbar", bool), true);
    return 1;
  }

  private static int executeSetAutomaticCompassUpdate(CommandSourceStack source, boolean bool) {
    config.setAutomaticCompassUpdate(bool);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.setautomaticcompassupdate", bool), true);
    return 1;
  }

  private static int executeSetAutomaticCompassUpdateDelay(CommandSourceStack source, int time) {
    config.setAutomaticCompassDelay(time);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.setautomaticcompassdelay", time), true);
    return 1;
  }

  private static int executeShowRunnerDimension(CommandSourceStack source, boolean bool) {
    config.setShowRunnerDimension(bool);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.showrunnerdimension", bool), true);
    return 1;
  }

  private static int executeFriendlyFire(CommandSourceStack source, boolean bool) {
    Collection<String> teams = source.getAllTeams();
    Scoreboard scoreboard = source.getServer().getScoreboard();

    if(teams.contains("hunters"))
      scoreboard.getPlayerTeam("hunters").setAllowFriendlyFire(bool);
    if(teams.contains("runners"))
      scoreboard.getPlayerTeam("runners").setAllowFriendlyFire(bool);
    source.sendSuccess(() -> Component.translatable("manhunt.commands.friendlyfire", bool), true);
    return 1;
  }

  private static int executeReload(CommandSourceStack source) {
    config.load();
    source.sendSuccess(() -> Component.translatable("manhunt.commands.reload"), true);
    return 1;
  }
}
