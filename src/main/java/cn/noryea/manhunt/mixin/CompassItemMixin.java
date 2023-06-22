package cn.noryea.manhunt.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CompassItem.class)
public abstract class CompassItemMixin extends Item {

  public CompassItemMixin(Properties settings) {
    super(settings);
  }

  public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
    return !(miner.getMainHandItem().getOrCreateTag().getBoolean("Tracker") && miner.isCreative());
  }

}
