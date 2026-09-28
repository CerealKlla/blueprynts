package com.github.cerealklla.blueprynts.construction;

import java.util.List;

import com.github.cerealklla.blueprynts.blueprint.BlueprintType;
import com.github.cerealklla.blueprynts.blueprint.BlueprintTypeRegistry;
import com.github.cerealklla.blueprynts.registration.ModBlockEntities;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Construction Site block -- v1 visually just a crafting-bench-alike (see its blockstate/model
 * assets). {@code FACING} points toward whoever placed it (furnace convention); the build area
 * always extends the opposite way ({@code ConstructionSiteBlockEntity#intoSite}).
 *
 * <p>Auto-clear (walking away, or breaking this block while a session is active) is driven by
 * {@link #serverTick}, a lightweight periodic distance check -- see its own doc.
 */
public class ConstructionSiteBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ConstructionSiteBlock> CODEC = simpleCodec(ConstructionSiteBlock::new);

    // How far (in blocks) the claiming player may wander from BOTH the site block and the build
    // volume before the site auto-clears -- the anti-farming safeguard, not just tidiness.
    private static final double AUTO_CLEAR_DISTANCE = 5.0;
    private static final int TICK_INTERVAL = 20;

    public ConstructionSiteBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // getHorizontalDirection() is the placer's own look direction (away from them) -- furnace
        // convention (and this block's own doc) wants FACING to point *toward* the placer instead,
        // so this needs the opposite. Getting this backwards was a real playtest bug: the leveled
        // clearing area extended toward the placer instead of away from them.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionSiteBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, pos, st, blockEntity) -> {
            if (lvl.getGameTime() % TICK_INTERVAL == 0 && blockEntity instanceof ConstructionSiteBlockEntity site) {
                serverTick((ServerLevel) lvl, pos, site);
            }
        };
    }

    /** Auto-clears an active session once the claiming player is more than {@link #AUTO_CLEAR_DISTANCE} from BOTH the site block and the outer clearing/build area. */
    private static void serverTick(ServerLevel level, BlockPos pos, ConstructionSiteBlockEntity site) {
        if (site.phase() == ConstructionSitePhase.IDLE || site.activePlayer() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(site.activePlayer());
        if (player == null) {
            return;
        }
        // Horizontal (X/Z) distance only -- BlockPos#distSqr is 3D, and standing at the site's own
        // Y vs. a player's feet position (one block above whatever they're standing on) was enough
        // vertical difference on its own to push a genuinely "5 blocks away" horizontal distance
        // over the threshold, a real playtest bug (walking right up to the edge of the build area
        // triggered an immediate auto-clear instead of being recognized as "still there").
        if (horizontalDistance(pos, player.getX(), player.getZ()) <= AUTO_CLEAR_DISTANCE) {
            return;
        }
        // Distance to the whole outer clearing rectangle, not individual marked columns -- a real
        // playtest bug: checking only placed-slab columns meant standing anywhere inside the
        // leveled area that hadn't been given a slab yet (including the entire area before any
        // slabs are placed at all) still counted as "away," triggering an immediate auto-clear the
        // instant a player walked in. The outer area always contains every marked column anyway, so
        // this is a strict superset of that check, not just a different one.
        if (site.outerArea().distanceTo(player.getX(), player.getZ()) <= AUTO_CLEAR_DISTANCE) {
            return;
        }
        site.restoreAndReset(level);
        player.sendSystemMessage(Component.literal("You wandered away from the Construction Site -- it's been cleared."));
    }

    private static double horizontalDistance(BlockPos a, double x, double z) {
        return horizontalDistance(a.getX(), a.getZ(), x, z);
    }

    private static double horizontalDistance(double ax, double az, double x, double z) {
        double dx = ax - x;
        double dz = az - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site)) {
            return InteractionResult.SUCCESS;
        }
        if (!site.claim(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.literal("Someone else is already using this Construction Site."));
            return InteractionResult.SUCCESS_SERVER;
        }
        sendScreen(serverPlayer, pos, site);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Rebuilds and sends a fresh screen snapshot -- called after every server-side mutation so the open screen always reflects authoritative state. */
    public static void sendScreen(ServerPlayer player, BlockPos pos, ConstructionSiteBlockEntity site) {
        List<String> typeIds = BlueprintTypeRegistry.all().stream().map(t -> t.id().toString()).toList();
        List<String> typeLabels = BlueprintTypeRegistry.all().stream().map(BlueprintType::label).toList();
        List<String> savedNames = player.level() instanceof ServerLevel serverLevel
                ? com.github.cerealklla.blueprynts.blueprint.BlueprintStorage.get(serverLevel).listNames()
                : List.of();
        PacketDistributor.sendToPlayer(player, new OpenConstructionSiteScreenPayload(
                pos,
                site.phase().name(),
                site.sizeClass().name(),
                site.tier(),
                site.blueprintTypeId() == null ? "" : site.blueprintTypeId().toString(),
                typeIds,
                typeLabels,
                site.markedColumns().size(),
                savedNames));
    }

    /**
     * Called from {@code ConstructionProtectionListener}'s break-event handler (this version of the
     * game has no {@code Block#onRemove} lifecycle hook to hang this off of directly) -- restores
     * the site before the break itself proceeds, so a claimed Construction Site is never left
     * half-modified just because its own block got mined.
     */
    public static void restoreIfActive(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ConstructionSiteBlockEntity site && site.phase() != ConstructionSitePhase.IDLE) {
            site.restoreAndReset(level);
        }
    }
}
