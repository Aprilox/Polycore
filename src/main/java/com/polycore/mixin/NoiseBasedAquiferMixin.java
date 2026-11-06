package com.polycore.mixin;

import com.polycore.gen.WorldgenHooks;
import com.polycore.optim.Optimization;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Fast aquifers. For every air or fluid block, vanilla looks for the 3 nearest aquifer centers among 12 candidates,
 * decoding each packed position and computing the full squared distance. Blocks are visited z first, so the
 * candidates and the x/y part of each distance usually stay the same from one block to the next: Polycore keeps them
 * and only adds the z part. Distances are integers, so the result is exactly vanilla's (the switch-off path below is
 * vanilla's code, and a game test compares both block by block). Not applied with C2ME or Noisium (PolyCoreMixinPlugin).
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
abstract class NoiseBasedAquiferMixin {
    @Shadow
    protected boolean shouldScheduleFluidUpdate;
    @Shadow
    @Final
    protected long[] aquiferLocationCache;
    @Shadow
    @Final
    private Aquifer.FluidPicker globalFluidPicker;
    @Shadow
    @Final
    private PositionalRandomFactory positionalRandomFactory;
    @Shadow
    @Final
    private static double FLOWING_UPDATE_SIMULARITY;

    @Shadow
    protected abstract int getIndex(int gridX, int gridY, int gridZ);

    @Shadow
    private Aquifer.FluidStatus getAquiferStatus(long packedPos) {
        throw new AssertionError();
    }

    @Shadow
    private double calculatePressure(DensityFunction.FunctionContext context, MutableDouble substance, Aquifer.FluidStatus firstFluid,
                                     Aquifer.FluidStatus secondFluid) {
        throw new AssertionError();
    }

    @Shadow
    protected static double similarity(int firstDistance, int secondDistance) {
        throw new AssertionError();
    }

    // Result of the nearest-centers search: squared distances and packed positions, closest first.
    @Unique
    private int polycore$d1, polycore$d2, polycore$d3;
    @Unique
    private long polycore$p1, polycore$p2, polycore$p3;

    // Fast path cache: the 12 candidates of the current grid cell and the x/y part of their distances.
    @Unique
    private int polycore$cellX = Integer.MIN_VALUE, polycore$cellY, polycore$cellZ;
    @Unique
    private int polycore$partX = Integer.MIN_VALUE, polycore$partY;
    @Unique
    private final long[] polycore$pos = new long[12];
    @Unique
    private final int[] polycore$cx = new int[12], polycore$cy = new int[12], polycore$cz = new int[12], polycore$part = new int[12];

    /**
     * @author Aprilox
     * @reason Same result as vanilla, with the 3-nearest search reusing the work shared by neighbouring blocks.
     */
    @Overwrite
    @Nullable
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
        int i = context.blockX();
        int j = context.blockY();
        int k = context.blockZ();
        if (substance > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }
        Aquifer.FluidStatus aquifer$fluidstatus = this.globalFluidPicker.computeFluid(i, j, k);
        if (aquifer$fluidstatus.at(j).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = false;
            return Blocks.LAVA.defaultBlockState();
        }
        if (WorldgenHooks.fastAquifer()) {
            Optimization.FAST_AQUIFER.markHookSeen();
            this.polycore$nearestFast(i, j, k);
        } else {
            this.polycore$nearestVanilla(i, j, k);
        }
        int k1 = this.polycore$d1, l1 = this.polycore$d2, i2 = this.polycore$d3;
        long j2 = this.polycore$p1, k2 = this.polycore$p2, l2 = this.polycore$p3;

        // From here on: vanilla.
        Aquifer.FluidStatus aquifer$fluidstatus1 = this.getAquiferStatus(j2);
        double d1 = similarity(k1, l1);
        BlockState blockstate = aquifer$fluidstatus1.at(j);
        if (d1 <= 0.0) {
            this.shouldScheduleFluidUpdate = d1 >= FLOWING_UPDATE_SIMULARITY;
            return blockstate;
        } else if (blockstate.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return blockstate;
        } else {
            MutableDouble mutabledouble = new MutableDouble(Double.NaN);
            Aquifer.FluidStatus aquifer$fluidstatus2 = this.getAquiferStatus(k2);
            double d2 = d1 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus2);
            if (substance + d2 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                Aquifer.FluidStatus aquifer$fluidstatus3 = this.getAquiferStatus(l2);
                double d0 = similarity(k1, i2);
                if (d0 > 0.0) {
                    double d3 = d1 * d0 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus3);
                    if (substance + d3 > 0.0) {
                        this.shouldScheduleFluidUpdate = false;
                        return null;
                    }
                }

                double d4 = similarity(l1, i2);
                if (d4 > 0.0) {
                    double d5 = d1 * d4 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus2, aquifer$fluidstatus3);
                    if (substance + d5 > 0.0) {
                        this.shouldScheduleFluidUpdate = false;
                        return null;
                    }
                }

                this.shouldScheduleFluidUpdate = true;
                return blockstate;
            }
        }
    }

    /** Vanilla's aquifer center of a grid cell (cached, created on first use). */
    @Unique
    private long polycore$location(int gridX, int gridY, int gridZ) {
        int k4 = this.getIndex(gridX, gridY, gridZ);
        long i5 = this.aquiferLocationCache[k4];
        if (i5 != Long.MAX_VALUE) return i5;
        RandomSource randomsource = this.positionalRandomFactory.at(gridX, gridY, gridZ);
        long l4 = BlockPos.asLong(gridX * 16 + randomsource.nextInt(10), gridY * 12 + randomsource.nextInt(9), gridZ * 16 + randomsource.nextInt(10));
        this.aquiferLocationCache[k4] = l4;
        return l4;
    }

    /** Vanilla's search, unchanged. */
    @Unique
    private void polycore$nearestVanilla(int i, int j, int k) {
        int l = Math.floorDiv(i - 5, 16);
        int i1 = Math.floorDiv(j + 1, 12);
        int j1 = Math.floorDiv(k - 5, 16);
        int k1 = Integer.MAX_VALUE, l1 = Integer.MAX_VALUE, i2 = Integer.MAX_VALUE;
        long j2 = 0L, k2 = 0L, l2 = 0L;
        for (int i3 = 0; i3 <= 1; i3++) {
            for (int j3 = -1; j3 <= 1; j3++) {
                for (int k3 = 0; k3 <= 1; k3++) {
                    long l4 = this.polycore$location(l + i3, i1 + j3, j1 + k3);
                    int i6 = BlockPos.getX(l4) - i;
                    int j5 = BlockPos.getY(l4) - j;
                    int k5 = BlockPos.getZ(l4) - k;
                    int l5 = i6 * i6 + j5 * j5 + k5 * k5;
                    if (k1 >= l5) {
                        l2 = k2;
                        k2 = j2;
                        j2 = l4;
                        i2 = l1;
                        l1 = k1;
                        k1 = l5;
                    } else if (l1 >= l5) {
                        l2 = k2;
                        k2 = l4;
                        i2 = l1;
                        l1 = l5;
                    } else if (i2 >= l5) {
                        l2 = l4;
                        i2 = l5;
                    }
                }
            }
        }
        this.polycore$d1 = k1;
        this.polycore$d2 = l1;
        this.polycore$d3 = i2;
        this.polycore$p1 = j2;
        this.polycore$p2 = k2;
        this.polycore$p3 = l2;
    }

    /** Same candidates in the same order and same comparisons; only the shared work is kept between blocks. */
    @Unique
    private void polycore$nearestFast(int i, int j, int k) {
        int l = Math.floorDiv(i - 5, 16);
        int i1 = Math.floorDiv(j + 1, 12);
        int j1 = Math.floorDiv(k - 5, 16);
        long[] pos = this.polycore$pos;
        int[] cx = this.polycore$cx, cy = this.polycore$cy, cz = this.polycore$cz, part = this.polycore$part;
        if (l != this.polycore$cellX || i1 != this.polycore$cellY || j1 != this.polycore$cellZ) {
            int n = 0;
            for (int i3 = 0; i3 <= 1; i3++) {
                for (int j3 = -1; j3 <= 1; j3++) {
                    for (int k3 = 0; k3 <= 1; k3++) {
                        long p = this.polycore$location(l + i3, i1 + j3, j1 + k3);
                        pos[n] = p;
                        cx[n] = BlockPos.getX(p);
                        cy[n] = BlockPos.getY(p);
                        cz[n] = BlockPos.getZ(p);
                        n++;
                    }
                }
            }
            this.polycore$cellX = l;
            this.polycore$cellY = i1;
            this.polycore$cellZ = j1;
            this.polycore$partX = Integer.MIN_VALUE;
        }
        if (i != this.polycore$partX || j != this.polycore$partY) {
            for (int n = 0; n < 12; n++) {
                int dx = cx[n] - i, dy = cy[n] - j;
                part[n] = dx * dx + dy * dy; // int sums wrap the same in any order
            }
            this.polycore$partX = i;
            this.polycore$partY = j;
        }
        int k1 = Integer.MAX_VALUE, l1 = Integer.MAX_VALUE, i2 = Integer.MAX_VALUE;
        long j2 = 0L, k2 = 0L, l2 = 0L;
        for (int n = 0; n < 12; n++) {
            int dz = cz[n] - k;
            int l5 = part[n] + dz * dz;
            if (k1 >= l5) {
                l2 = k2;
                k2 = j2;
                j2 = pos[n];
                i2 = l1;
                l1 = k1;
                k1 = l5;
            } else if (l1 >= l5) {
                l2 = k2;
                k2 = pos[n];
                i2 = l1;
                l1 = l5;
            } else if (i2 >= l5) {
                l2 = pos[n];
                i2 = l5;
            }
        }
        this.polycore$d1 = k1;
        this.polycore$d2 = l1;
        this.polycore$d3 = i2;
        this.polycore$p1 = j2;
        this.polycore$p2 = k2;
        this.polycore$p3 = l2;
    }
}
