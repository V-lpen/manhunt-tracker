package cn.noryea.manhunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class Manhunt implements ModInitializer {

  public static List<ServerPlayer> allPlayers;
  public static List<ServerPlayer> allRunners;

  public static Logger LOGGER = LoggerFactory.getLogger("manhunt");

  @Override
  public void onInitialize() {
    ManhuntConfig config = ManhuntConfig.INSTANCE;
    config.load();
    ServerTickEvents.START_WORLD_TICK.register((world) -> {
      world.getServer().getCommands().performPrefixedCommand(world.getServer().createCommandSourceStack().withSuppressedOutput(), "kill @e[type=item,nbt={Item:{tag:{Tracker:1b}}}]");

      Scoreboard scoreboard = world.getServer().getScoreboard();
      if (scoreboard.getPlayerTeam("hunters") == null) {
        PlayerTeam team = scoreboard.addPlayerTeam("hunters");
        team.setDisplayName(Component.translatable("manhunt.teams.hunters.name"));
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        team.setSeeFriendlyInvisibles(false);
      }
      scoreboard.getPlayerTeam("hunters").setColor(config.getHuntersColor());

      if (scoreboard.getPlayerTeam("runners") == null) {
        PlayerTeam team = scoreboard.addPlayerTeam("runners");
        team.setDisplayName(Component.translatable("manhunt.teams.runners.name"));
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        team.setSeeFriendlyInvisibles(false);
      }
      scoreboard.getPlayerTeam("runners").setColor(config.getRunnersColor());

      allPlayers = world.getServer().getPlayerList().getPlayers();
      allRunners = new LinkedList<>();

      PlayerTeam runners = scoreboard.getPlayerTeam("runners");
      for (ServerPlayer x : allPlayers) {
        if (x != null) {
          if (x.isAlliedTo(runners)) {
            allRunners.add(x);
          }
        }
      }

    });

    CommandRegistrationCallback.EVENT.register(ManhuntCommand::registerCommands);

  }
}
