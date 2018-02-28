package com.creativemd.littletiles.common.utils.placing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.cert.CRLReason;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.creativecore.common.utils.TickUtils;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.common.api.ILittleTile;
import com.creativemd.littletiles.common.blocks.BlockTile;
import com.creativemd.littletiles.common.mods.chiselsandbits.ChiselsAndBitsManager;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.tiles.LittleTile;
import com.creativemd.littletiles.common.tiles.place.FixedHandler;
import com.creativemd.littletiles.common.tiles.place.InsideFixedHandler;
import com.creativemd.littletiles.common.tiles.place.PlacePreviewTile;
import com.creativemd.littletiles.common.tiles.preview.LittleTilePreview;
import com.creativemd.littletiles.common.tiles.vec.LittleTileBox;
import com.creativemd.littletiles.common.tiles.vec.LittleTileSize;
import com.creativemd.littletiles.common.tiles.vec.LittleTileVec;
import com.creativemd.littletiles.common.utils.placing.PlacementHelper.PositionResult;
import com.creativemd.littletiles.common.utils.placing.PlacementMode.SelectionMode;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**This class does all caculate on where to place a block. Used for rendering preview and placing**/
public class PlacementHelper {
	
	public static class PositionResult {
		
		public BlockPos pos;
		public LittleTileVec hit;
		public EnumFacing facing;
		
		public static PositionResult readFromBytes(ByteBuf buf)
		{
			PositionResult result = new PositionResult();
			result.pos = CreativeCorePacket.readPos(buf);
			result.facing = CreativeCorePacket.readFacing(buf);
			result.hit = new LittleTileVec(buf.readInt(), buf.readInt(), buf.readInt());
			return result;
		}
		
		public LittleTileVec getAbsoluteVec()
		{
			LittleTileVec absolute = new LittleTileVec(pos);
			absolute.add(hit);
			return absolute;
		}
		
		public void subVec(LittleTileVec vec)
		{
			hit.add(vec);
			updatePos();
		}
		
		public void addVec(LittleTileVec vec)
		{
			hit.sub(vec);
			updatePos();
		}
		
		public void writeToBytes(ByteBuf buf)
		{
			CreativeCorePacket.writePos(buf, pos);
			CreativeCorePacket.writeFacing(buf, facing);
			buf.writeInt(hit.x);
			buf.writeInt(hit.y);
			buf.writeInt(hit.z);
		}
		
		private void updatePos()
		{
			//Larger
			if(hit.x >= LittleTile.gridSize)
			{
				int amount = hit.x / LittleTile.gridSize;
				hit.x -= amount * LittleTile.gridSize;
				pos = pos.add(amount, 0, 0);
			}
			if(hit.y >= LittleTile.gridSize)
			{
				int amount = hit.y / LittleTile.gridSize;
				hit.y -= amount * LittleTile.gridSize;
				pos = pos.add(0, amount, 0);
			}
			if(hit.z >= LittleTile.gridSize)
			{
				int amount = hit.z / LittleTile.gridSize;
				hit.z -= amount * LittleTile.gridSize;
				pos = pos.add(0, 0, amount);
			}
			
			//Smaller
			if(hit.x < 0)
			{
				int amount = (int) Math.ceil(Math.abs(hit.x / (double) LittleTile.gridSize));
				hit.x += amount * LittleTile.gridSize;
				pos = pos.add(-amount, 0, 0);
			}
			if(hit.y < 0)
			{
				int amount = (int) Math.ceil(Math.abs(hit.y / (double) LittleTile.gridSize));
				hit.y += amount * LittleTile.gridSize;
				pos = pos.add(0, -amount, 0);
			}
			if(hit.z < 0)
			{
				int amount = (int) Math.ceil(Math.abs(hit.z / (double) LittleTile.gridSize));
				hit.z += amount * LittleTile.gridSize;
				pos = pos.add(0, 0, -amount);
			}
		}

		public PositionResult copy()
		{
			PositionResult result = new PositionResult();
			result.facing = facing;
			result.pos = pos;
			result.hit = hit.copy();
			return result;
		}
	}
	
	public static class PreviewResult {
		
		public List<PlacePreviewTile> placePreviews = new ArrayList<>();
		public List<LittleTilePreview> previews = null;
		public LittleTileBox box;
		public LittleTileSize size;
		public boolean singleMode = false;
		public boolean placedFixed = false;
		public LittleTileVec offset;
		
	}
	
	public static ILittleTile getLittleInterface(ItemStack stack)
	{
		if(stack == null)
			return null;
		if(stack.getItem() instanceof ILittleTile)
			return (ILittleTile) stack.getItem();
		if(Block.getBlockFromItem(stack.getItem()) instanceof ILittleTile)
			return (ILittleTile)Block.getBlockFromItem(stack.getItem());
		return null;
	}
	
	public static boolean isLittleBlock(ItemStack stack)
	{
		if(stack == null)
			return false;
		if(stack.getItem() instanceof ILittleTile)
			return ((ILittleTile) stack.getItem()).hasLittlePreview(stack);
		if(Block.getBlockFromItem(stack.getItem()) instanceof ILittleTile)
			return ((ILittleTile)Block.getBlockFromItem(stack.getItem())).hasLittlePreview(stack);
		return false;
	}
	
	public static LittleTileVec getInternalOffset(List<LittleTilePreview> tiles)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (int i = 0; i < tiles.size(); i++) {
			LittleTilePreview tile = tiles.get(i);
			if(tile == null)
				return new LittleTileVec(0, 0, 0);
			if(tile.box != null)
			{
				minX = Math.min(minX, tile.box.minX);
				minY = Math.min(minY, tile.box.minY);
				minZ = Math.min(minZ, tile.box.minZ);
			}
		}
		return new LittleTileVec(minX, minY, minZ);
	}
	
	public static LittleTileSize getSize(List<LittleTilePreview> tiles)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		LittleTileSize size = new LittleTileSize(0, 0, 0);
		for (int i = 0; i < tiles.size(); i++) {
			LittleTilePreview tile = tiles.get(i);
			if(tile == null)
				return new LittleTileSize(0, 0, 0);
			minX = Math.min(minX, tile.box.minX);
			minY = Math.min(minY, tile.box.minY);
			minZ = Math.min(minZ, tile.box.minZ);
			maxX = Math.max(maxX, tile.box.maxX);
			maxY = Math.max(maxY, tile.box.maxY);
			maxZ = Math.max(maxZ, tile.box.maxZ);
		}
		return new LittleTileSize(maxX-minX, maxY-minY, maxZ-minZ).max(size);
	}
	
	public static void removeCache()
	{
		lastCached = null;
		lastPreviews = null;
	}
	
	private static NBTTagCompound lastCached;
	private static ArrayList<LittleTilePreview> lastPreviews;
	
	public static PositionResult getPosition(World world, RayTraceResult moving)
	{
		PositionResult result = new PositionResult();
		
		int x = moving.getBlockPos().getX();
		int y = moving.getBlockPos().getY();
		int z = moving.getBlockPos().getZ();
		
		boolean canBePlacedInsideBlock = true;
		if(!canBePlacedInside(world, moving.getBlockPos(), moving.hitVec, moving.sideHit))
		{
			switch(moving.sideHit)
			{
			case EAST:
				x++;
				break;
			case WEST:
				x--;
				break;
			case UP:
				y++;
				break;
			case DOWN:
				y--;
				break;
			case SOUTH:
				z++;
				break;
			case NORTH:
				z--;
				break;
			default:
				break;
			}
			
			canBePlacedInsideBlock = false;
		}
		
		result.facing = moving.sideHit;
		result.pos = new BlockPos(x, y, z);
		result.hit = getHitVec(moving, canBePlacedInsideBlock);
		
		return result;
	}
	
	/**
	 * @param centered if the previews should be centered
	 * @param facing if centered is true it will be used to apply the offset
	 * @param fixed if the previews should keep it's original boxes
	 */
	public static PreviewResult getPreviews(World world, ItemStack stack, PositionResult position, boolean centered, boolean fixed, boolean allowLowResolution, boolean marked, PlacementMode mode)
	{
		return getPreviews(world, stack, position.pos, position.hit, centered, position.facing, fixed, allowLowResolution, marked, mode);
	}
	
	/**
	 * @param hit relative vector to pos
	 * @param centered if the previews should be centered
	 * @param facing if centered is true it will be used to apply the offset
	 * @param fixed if the previews should keep it's original boxes
	 */
	public static PreviewResult getPreviews(World world, ItemStack stack, BlockPos pos, LittleTileVec hit, boolean centered, @Nullable EnumFacing facing, boolean fixed, boolean allowLowResolution, boolean marked, PlacementMode mode)
	{
		PreviewResult result = new PreviewResult();
		
		ILittleTile iTile = PlacementHelper.getLittleInterface(stack);
		
		List<LittleTilePreview> tiles = allowLowResolution && iTile.shouldCache() && lastCached != null && lastCached.equals(stack.getTagCompound()) ? new ArrayList<>(lastPreviews) : null;
		
		if(tiles == null && iTile != null)
			tiles = iTile.getLittlePreview(stack, allowLowResolution, marked);
		
		if(tiles != null && tiles.size() > 0)
		{
			result.previews = tiles;
			
			result.size = getSize(tiles);
			
			ArrayList<FixedHandler> shifthandlers = new ArrayList<FixedHandler>();
			
			if(tiles.size() == 1)
			{
				shifthandlers.addAll(tiles.get(0).fixedhandlers);
				shifthandlers.add(new InsideFixedHandler());
				result.singleMode = true;
				centered = true;
			}
			
			result.box = getTilesBox(hit, result.size, centered, facing, mode);
			
			boolean canBePlaceFixed = false;
			
			if(fixed)
			{
				if(!result.singleMode)
				{
					Block block = world.getBlockState(pos).getBlock();
					if(block.isReplaceable(world, pos) || block instanceof BlockTile)
					{
						canBePlaceFixed = true;
						if(mode.mode == SelectionMode.PREVIEWS)
						{
							TileEntity te = world.getTileEntity(pos);
							if(te instanceof TileEntityLittleTiles)
							{
								TileEntityLittleTiles teTiles = (TileEntityLittleTiles) te;
								for (int i = 0; i < tiles.size(); i++) {
									LittleTilePreview tile = tiles.get(i);
									if(!teTiles.isSpaceForLittleTile(tile.box))
									{
										canBePlaceFixed = false;
										break;
									}
								}
							}
						}
					}
				}
				
				if(!canBePlaceFixed)
				{
					for (int i = 0; i < shifthandlers.size(); i++) {
						shifthandlers.get(i).init(world, pos);
					}
					
					FixedHandler handler = null;
					double distance = 2;
					for (int i = 0; i < shifthandlers.size(); i++) {
						double tempDistance = shifthandlers.get(i).getDistance(hit);
						if(tempDistance < distance)
						{
							distance = tempDistance;
							handler = shifthandlers.get(i);
						}
					}
					
					if(handler != null)
						result.box = handler.getNewPosition(world, pos, result.box);
				}
			}
			
			LittleTileVec offset = result.box.getMinVec();
			LittleTileVec internalOffset = getInternalOffset(tiles);
			internalOffset.invert();
			offset.add(internalOffset);
			
			result.offset = offset;
			
			result.placedFixed = canBePlaceFixed;
			
			//Generating placetiles
			for (int i = 0; i < tiles.size(); i++) {
				LittleTilePreview tile = tiles.get(i);
				if(tile != null)
				{
					PlacePreviewTile preview = tile.getPlaceableTile(result.box, canBePlaceFixed, offset);
					if(preview != null)
					{
						if((canBePlaceFixed || (fixed && result.singleMode)) && mode.mode == SelectionMode.LINES)
							if(hit.getAxis(facing.getAxis()) % LittleTile.gridSize == 0)
								preview.box.addOffset(facing.getOpposite().getDirectionVec());
						result.placePreviews.add(preview);
					}
				}
			}
			
			LittleStructure structure = iTile.getLittleStructure(stack);
			if(structure != null)
			{
				ArrayList<PlacePreviewTile> newBoxes = structure.getSpecialTiles();
				
				for (int i = 0; i < newBoxes.size(); i++) {
					if(!canBePlaceFixed)
						newBoxes.get(i).box.addOffset(offset);
				}
				
				result.placePreviews.addAll(newBoxes);
			}
			
			if(allowLowResolution)
			{
				if(stack.getTagCompound() == null)
				{
					lastCached = null;
					lastPreviews = null;
				}else{
					lastCached = stack.getTagCompound().copy();
					lastPreviews = new ArrayList<>(tiles);
				}
			}
			
			return result;
		}
		
		return null;
	}
	
	public static LittleTileBox getTilesBox(LittleTileVec hit, LittleTileSize size, boolean centered, @Nullable EnumFacing facing, PlacementMode mode)
	{
		LittleTileVec temp = hit.copy();
		if(centered)
		{
			LittleTileVec center = size.calculateCenter();
			LittleTileVec centerInv = size.calculateInvertedCenter();
			
			if(mode.mode == SelectionMode.LINES)
				facing = facing.getOpposite();
			
			//Make hit the center of the Box
			switch(facing)
			{
			case EAST:
				temp.x += center.x;
				break;
			case WEST:
				temp.x -= centerInv.x;
				break;
			case UP:
				temp.y += center.y;
				break;
			case DOWN:
				temp.y -= centerInv.y;
				break;
			case SOUTH:
				temp.z += center.z;
				break;
			case NORTH:
				temp.z -= centerInv.z;
				break;
			default:
				break;
			}
		}
		return new LittleTileBox(temp, size);
	}
	
	public static boolean canBlockBeUsed(World world, BlockPos pos)
	{
		TileEntity tileEntity = world.getTileEntity(pos);
		if(tileEntity instanceof TileEntityLittleTiles)
			return true;
		return ChiselsAndBitsManager.isChiselsAndBitsStructure(tileEntity);
	}
	
	public static boolean canBePlacedInside(World world, BlockPos pos, Vec3d hitVec, EnumFacing side)
	{
		if(canBlockBeUsed(world, pos))
		{
			switch(side)
			{
			case EAST:
			case WEST:
				return (int)hitVec.x != hitVec.x;
			case UP:
			case DOWN:
				return (int)hitVec.y != hitVec.y;
			case SOUTH:
			case NORTH:
				return (int)hitVec.z != hitVec.z;
			default:
				return false;
			}
		}
		return false;
	}
	
	public static LittleTileVec getHitVec(RayTraceResult result, boolean isInsideOfBlock)
	{
		
		LittleTileVec vec = new LittleTileVec(result);
		vec.sub(result.getBlockPos());
		
		if(!isInsideOfBlock)
			vec.setAxis(result.sideHit.getAxis(), result.sideHit.getAxisDirection() == AxisDirection.POSITIVE ? 0 : LittleTile.gridSize);
		
		return vec;
	}
}
