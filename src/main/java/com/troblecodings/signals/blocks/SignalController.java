package com.troblecodings.signals.blocks;

import com.troblecodings.signals.init.SignaIItems;
import com.troblecodings.signals.init.SignalTabs;
import com.troblecodings.signals.tileentitys.SignalControllerTileEntity;

import eu.gir.guilib.ecs.GuiHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignalController extends Block implements ITileEntityProvider {

    public SignalController() {
        super(Material.ROCK);
        setCreativeTab(SignalTabs.TAB);
    }

    @Override
    public boolean onBlockActivated(final World worldIn, final BlockPos pos,
            final IBlockState state, final EntityPlayer playerIn, final EnumHand hand,
            final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        if (!playerIn.getHeldItemMainhand().getItem().equals(SignaIItems.LINKING_TOOL)) {
            if (worldIn.isRemote)
                return true;
            GuiHandler.invokeGui(SignalController.class, playerIn, worldIn, pos);
            return true;
        }
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(final World worldIn, final int meta) {
        return new SignalControllerTileEntity();
    }

    @Override
    public void neighborChanged(final IBlockState state, final World world, final BlockPos pos,
            final Block blockIn, final BlockPos fromPos) {
        final TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof SignalControllerTileEntity) {
            ((SignalControllerTileEntity) tile).redstoneUpdate();
        }
    }
}
