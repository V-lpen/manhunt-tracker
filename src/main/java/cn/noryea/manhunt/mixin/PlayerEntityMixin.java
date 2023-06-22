package cn.noryea.manhunt.mixin;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(Player.class)
public abstract class PlayerEntityMixin extends LivingEntity {

  ListTag positions = new ListTag();

  protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level world) {
    super(entityType, world);
  }

  @Inject(method = "tick", at = @At("HEAD"))
  public void tick(CallbackInfo ci) {

    DataResult<Tag> var10000 = Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level().dimension());
    Logger logger = LoggerFactory.getLogger("Manhunt");
    Objects.requireNonNull(logger);
    var10000.resultOrPartial(logger::error).ifPresent((dimension) -> {
      for (int i = 0; i < positions.size(); ++i) {
        CompoundTag compound = positions.getCompound(i);
        if (Objects.equals(compound.getString("LodestoneDimension"), dimension.getAsString())) {
          positions.remove(compound);
        }
      }
      CompoundTag nbtCompound = new CompoundTag();
      nbtCompound.put("LodestonePos", NbtUtils.writeBlockPos(this.blockPosition()));
      nbtCompound.put("LodestoneDimension", dimension);
      positions.add(nbtCompound);
    });
  }

  @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
  public void addAdditionalSaveData(CompoundTag nbt, CallbackInfo cbi) {
    nbt.putBoolean("manhuntModded", true);
    nbt.put("Positions", positions);
  }

  @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
  public void readAdditionalSaveData(CompoundTag nbt, CallbackInfo cbi) {
    this.positions = nbt.getList("Positions", 10);
  }
}
