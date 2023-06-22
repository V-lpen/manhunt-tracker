package cn.noryea.manhunt.mixin;

import cn.noryea.manhunt.Manhunt;
import cn.noryea.manhunt.ManhuntConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {

  private final ManhuntConfig config = ManhuntConfig.INSTANCE;

  @Final
  @Shadow
  protected ServerPlayer player;

  @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
  public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
    if (action.equals(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK)) {
      cycleTrackedPlayer(this.player, this.player.getMainHandItem().getTag());
    }
  }

  @Inject(method = "destroyBlock", at = @At("HEAD"))
  public void destroyBlock(BlockPos pos, CallbackInfoReturnable<InteractionResult> ci) {
    cycleTrackedPlayer(this.player, this.player.getMainHandItem().getTag());
  }

  @Inject(
      method = "useItem",
      at = @At(
          target = "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
          value = "INVOKE"
      ))
  public void useItem(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cbi) {
    if (stack.getTag() != null && stack.getTag().getBoolean("Tracker") && !player.isSpectator() && player.isAlliedTo(world.getScoreboard().getPlayerTeam("hunters"))) {
      player.getCooldowns().addCooldown(stack.getItem(), config.getDelay() * 20);
      if (!stack.getOrCreateTag().contains("Info")) {
        stack.getOrCreateTag().put("Info", new CompoundTag());
      }
      CompoundTag info = stack.getOrCreateTag().getCompound("Info");

      if (!info.contains("Name", Tag.TAG_STRING) && !Manhunt.allRunners.isEmpty()) {
        info.putString("Name", Manhunt.allRunners.get(0).getName().getString());
      }

      ServerPlayer trackedPlayer = world.getServer().getPlayerList().getPlayerByName(info.getString("Name"));

      if (trackedPlayer != null) {
        player.connection.send(new ClientboundSoundPacket(SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.85f, 0.95f, 0));
        updateCompass(player, stack.getOrCreateTag(), trackedPlayer);
      }
    }
  }

  private void cycleTrackedPlayer(ServerPlayer player, @Nullable CompoundTag stackNbt) {
    if (stackNbt != null && stackNbt.getBoolean("Tracker") && player.isAlliedTo(player.getServer().getScoreboard().getPlayerTeam("hunters"))) {
      if (!stackNbt.contains("Info")) {
        stackNbt.put("Info", new CompoundTag());
      }

      int next;
      int previous = -1;
      CompoundTag info = stackNbt.getCompound("Info");

      if (Manhunt.allRunners.isEmpty()) player.sendSystemMessage(Component.translatable("manhunt.item.tracker.norunners"));
      else {
        for (int i = 0; i < Manhunt.allRunners.size(); i++) {
          ServerPlayer x = Manhunt.allRunners.get(i);
          if (x != null) {
            if (Objects.equals(x.getName().getString(), info.getString("Name"))) {
              previous = i;
            }
          }
        }

        if (previous + 1 >= Manhunt.allRunners.size()) {
          next = 0;
        } else {
          next = previous + 1;
        }

        if (previous != next) {
          updateCompass(player, stackNbt, Manhunt.allRunners.get(next));
          player.sendSystemMessage(Component.translatable("manhunt.item.tracker.switchrunner", Manhunt.allRunners.get(next).getName().getString()));
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
}
