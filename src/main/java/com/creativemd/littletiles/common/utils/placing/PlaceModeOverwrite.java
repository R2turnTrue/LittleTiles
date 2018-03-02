package com.creativemd.littletiles.common.utils.placing;

import java.util.ArrayList;
import java.util.List;

import com.creativemd.creativecore.common.utils.HashMapList;
import com.creativemd.littletiles.common.action.block.LittleActionDestroyBoxes;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.tiles.LittleTile;
import com.creativemd.littletiles.common.tiles.place.PlacePreviewTile;

import net.minecraft.util.math.BlockPos;

public class PlaceModeOverwrite extends PlacementMode {

	public PlaceModeOverwrite(String name, SelectionMode mode) {
		super(name, mode);
	}
	
	@Override
	public boolean shouldConvertBlock()
	{
		return true;
	}
	
	@Override
	public boolean canPlaceStructures() {
		return true;
	}
	
	@Override
	public boolean checkAll()
	{
		return false;
	}

	@Override
	public List<BlockPos> getCoordsToCheck(HashMapList<BlockPos, PlacePreviewTile> splittedTiles, BlockPos pos) {
		return new ArrayList<>(splittedTiles.keySet());
	}

	@Override
	public List<LittleTile> placeTile(TileEntityLittleTiles te, LittleTile tile, List<LittleTile> unplaceableTiles, List<LittleTile> removedTiles,
			boolean requiresCollisionTest) {
		List<LittleTile> tiles = new ArrayList<>();
		if(requiresCollisionTest)
			removedTiles.addAll(LittleActionDestroyBoxes.removeBox(te, tile.box));		
		tiles.add(tile);
		return tiles;
	}

}
