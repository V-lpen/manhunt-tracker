package cn.noryea.manhunt.mixin;

import cn.noryea.manhunt.Manhunt;
import cn.noryea.manhunt.ManhuntConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.text.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin extends Player {

  @Final
  @Shadow
  public MinecraftServer server;
  @Shadow
  public ServerGamePacketListenerImpl networkHandler;

  @Shadow
  public abstract boolean changeGameMode(GameType gameMode);

  @Shadow @Final public ServerPlayerGameMode interactionManager;

  boolean holding;
  ManhuntConfig config = ManhuntConfig.INSTANCE;
  private long lastDelay = System.currentTimeMillis();
  public ServerPlayerEntityMixin(Level world, BlockPos pos, float yaw, GameProfile profile, ProfilePublicKey key) {
    super(world, pos, yaw, profile);
  }

  @Inject(method = "tick", at = @At("HEAD"))
  public void tick(CallbackInfo ci) {
    if (this.isAlliedTo(server.getScoreboard().getPlayerTeam("hunters")) && this.isAlive()) {
      if (!hasTracker()) {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("Tracker", true);
        nbt.putBoolean("LodestoneTracked", false);
        nbt.putString("LodestoneDimension", "minecraft:overworld");
        nbt.putInt("HideFlags", 1);
        nbt.put("Info", new CompoundTag());
        nbt.put("display", new CompoundTag());
        nbt.getCompound("display").putString("Name", "{\"translate\": \"manhunt.item.tracker\",\"italic\": false,\"color\": \"white\"}");

        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.setTag(nbt);
        stack.enchant(Enchantments.VANISHING_CURSE, 1);

        this.addItem(stack);
      } else if (config.isAutomaticCompassUpdate() && (config.getAutomaticCompassDelay() == 0 || System.currentTimeMillis() - lastDelay > ((long) config.getAutomaticCompassDelay() * 1000))) {
        for (ItemStack item : this.getInventory().items) {
          if (item.getItem().equals(Items.COMPASS) && item.getTag() != null && item.getTag().getBoolean("Tracker")) {
            ServerPlayer trackedPlayer = server.getPlayerList().getPlayerByName(item.getTag().getCompound("Info").getString("Name"));
            if (trackedPlayer != null) {
              updateCompass((ServerPlayer) (Object) this, item.getTag(), trackedPlayer);
              this.getCooldowns().addCooldown(item.getItem(), config.getAutomaticCompassDelay() * 20);
            }
          }
        }
        lastDelay = System.currentTimeMillis();
      }


      if (holdingTracker()) {
        holding = true;
        if (this.getMainHandItem().getTag() != null && this.getMainHandItem().getTag().getBoolean("Tracker")) {
          CompoundTag info = this.getMainHandItem().getTag().getCompound("Info");
          if (server.getPlayerList().getPlayerByName(info.getString("Name")) != null) {
            showInfo(info);
          }
        } else if (this.getOffhandItem().getTag() != null) {
          CompoundTag info = this.getOffhandItem().getTag().getCompound("Info");
          if (server.getPlayerList().getPlayerByName(info.getString("Name")) != null) {
            showInfo(info);
          }
        }
      } else {
        if (holding) {
          this.networkHandler.send(new ClientboundSetActionBarTextPacket(Component.nullToEmpty("")));
          holding = false;
        }
      }

    }
  }

  private void updateCompass(ServerPlayer player, CompoundTag nbt, ServerPlayer trackedPlayer) {
    nbt.remove("LodestonePos");
    nbt.remove("LodestoneDimension");

    nbt.put("Info", new CompoundTag());
    if (trackedPlayer.getTeam() != null && Objects.equals(trackedPlayer.getTeam().getName(), "runners")) {
      CompoundTag playerTag = trackedPlayer.saveWithoutId(new CompoundTag());
      ListTag positions = playerTag.getList("Positions", 10);
      int i;
      for (i = 0; i < positions.size(); ++i) {
        CompoundTag compound = positions.getCompound(i);
        if (Objects.equals(compound.getString("LodestoneDimension"), player.saveWithoutId(new CompoundTag()).getString("Dimension"))) {
          nbt.merge(compound);
          break;
        }
      }

      CompoundTag info = nbt.getCompound("Info");
      info.putLong("LastUpdateTime", player.level().getGameTime());
      info.putString("Name", trackedPlayer.getScoreboardName());
      info.putString("Dimension", playerTag.getString("Dimension"));
    }

  }

  @Inject(method = "onDeath", at = @At("HEAD"))
  public void onDeath(DamageSource source, CallbackInfo ci) {
    Scoreboard scoreboard = server.getScoreboard();

    if (this.getTeam() != null) {
      if (this.getTeam().isAlliedTo(scoreboard.getPlayerTeam("runners"))) {

        changeGameMode(GameType.SPECTATOR);
        scoreboard.removePlayerFromTeam(this.getName().getString());

        if (server.getScoreboard().getPlayerTeam("runners").getPlayers().isEmpty()) {
          server.getCommands().performPrefixedCommand(this.createCommandSourceStack().withSuppressedOutput().withPermission(2), "title @a subtitle {\"translate\":\"manhunt.win.hunters.subtitle\",\"color\":\"white\"}");
          server.getCommands().performPrefixedCommand(this.createCommandSourceStack().withSuppressedOutput().withPermission(2), "title @a title {\"translate\":\"manhunt.win.hunters.title\",\"color\":\"white\"}"); //
        }
      }
    }
  }

  private void showInfo(CompoundTag info) {
    String dim = info.getString("Dimension");
    String dimension = "";
    if (!info.contains("Dimension")) {
      dimension = "manhunt.scoreboard.world.unknown";
    } else if (Objects.equals(dim, "minecraft:overworld")) {
      dimension = "manhunt.scoreboard.world.overworld";
    } else if (Objects.equals(dim, "minecraft:the_nether")) {
      dimension = "manhunt.scoreboard.world.the_nether";
    } else if (Objects.equals(dim, "minecraft:the_end")) {
      dimension = "manhunt.scoreboard.world.the_end";
    }

    if(config.isShowTitle()) {
      if(config.isShowRunnerDimension()) {
        this.networkHandler.send(new ClientboundSetActionBarTextPacket(Component.translatable("manhunt.scoreboard.target.text", info.getString("Name"), Component.translatable(dimension))));
      } else {
        this.networkHandler.send(new ClientboundSetActionBarTextPacket(Component.translatable("manhunt.scoreboard.target.textnodimension", info.getString("Name"))));
      }

    }
  }

  private boolean hasTracker() {
    boolean n = false;
    for (ItemStack item : this.getInventory().items) {
      if (item.getItem().equals(Items.COMPASS) && item.getTag() != null && item.getTag().getBoolean("Tracker")) {
        n = true;
        break;
      }
    }

    if (this.inventoryMenu.getCarried().getTag() != null && this.inventoryMenu.getCarried().getTag().getBoolean("Tracker")) {
      n = true;
    } else if (this.getOffhandItem().getTag() != null && this.getOffhandItem().getTag().getBoolean("Tracker")) {
      n = true;
    }

    return n;
  }

  private boolean holdingTracker() {
    boolean n = false;
    if (this.getMainHandItem().getTag() != null && this.getMainHandItem().getTag().getBoolean("Tracker") && this.getMainHandItem().getTag().getCompound("Info").contains("Name")) {
      n = true;
    } else if (this.getOffhandItem().getTag() != null && this.getOffhandItem().getTag().getBoolean("Tracker") && this.getOffhandItem().getTag().getCompound("Info").contains("Name")) {
      n = true;
    }
    return n;
  }

// 玩家列表的名字
//    @Inject(method = "getPlayerListName", at = @At("TAIL"), cancellable = true)
//    private void replacePlayerListName(CallbackInfoReturnable<Text> cir) {
//        try {
//            if (this.getScoreboardTeam() != null) {
//
//                Team team = server.getScoreboard().getTeam(this.getScoreboardTeam().getName());
//
//                MutableText mutableText = (new LiteralText("")).append(team.getFormattedName()).append(this.getName());
//
//                Formatting formatting = team.getColor();
//                if (formatting != Formatting.RESET) {
//                    mutableText.formatted(formatting);
//                } else if (team.getName().equals("hunters")) {
//                    mutableText.formatted(Manhunt.huntersColor);
//                } else if (team.getName().equals("runners")) {
//                    mutableText.formatted(Manhunt.runnersColor);
//                }
//
//                cir.setReturnValue(mutableText);
//
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
