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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fast aquifers. For every air or fluid block, vanilla looks for the 4 nearest aquifer centers among 12 candidates,
 * decoding each packed position and computing the full squared distance. Blocks are visited z first, so the
 * candidates and the x/y part of each distance usually stay the same from one block to the next: Polycore keeps them
 * and only adds the z part. Distances are integers, so the result is exactly vanilla's (a game test compares both
 * block by block). Vanilla's method is left untouched: when the feature is off, or another mod changes
 * computeSubstance (util/Conflicts), it simply runs, with that mod's changes.
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
    private int polycore$d1, polycore$d2, polycore$d3, polycore$d4;
    @Unique
    private long polycore$p1, polycore$p2, polycore$p3, polycore$p4;

    // Fast path cache: the 12 candidates of the current grid cell and the x/y part of their distances.
    @Unique
    private int polycore$cellX = Integer.MIN_VALUE, polycore$cellY, polycore$cellZ;
    @Unique
    private int polycore$partX = Integer.MIN_VALUE, polycore$partY;
    @Unique
    private final long[] polycore$pos = new long[12];
    @Unique
    private final int[] polycore$cx = new int[12], polycore$cy = new int[12], polycore$cz = new int[12], polycore$part = new int[12];

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void polycore$fastSubstance(DensityFunction.FunctionContext context, double substance, CallbackInfoReturnable<BlockState> cir) {
        if (!WorldgenHooks.fastAquifer()) return; // vanilla's own code runs
        Optimization.FAST_AQUIFER.markHookSeen();
        cir.setReturnValue(this.polycore$computeSubstance(context, substance));
    }

    /** Vanilla's computeSubstance with the fast nearest-centers search. */
    @Unique
    @Nullable
    private BlockState polycore$computeSubstance(DensityFunction.FunctionContext context, double substance) {
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
        this.polycore$nearestFast(i, j, k);
        int k1 = this.polycore$d1, l1 = this.polycore$d2, i2 = this.polycore$d3, j2 = this.polycore$d4;
        long k2 = this.polycore$p1, l2 = this.polycore$p2, i3 = this.polycore$p3, j3 = this.polycore$p4;

        // From here on: vanilla 1.21.4, unchanged.
        Aquifer.FluidStatus aquifer$fluidstatus1 = this.getAquiferStatus(k2);
        double d1 = similarity(k1, l1);
        BlockState blockstate = aquifer$fluidstatus1.at(j);
        if (d1 <= 0.0) {
            if (d1 >= FLOWING_UPDATE_SIMULARITY) {
                Aquifer.FluidStatus aquifer$fluidstatus2 = this.getAquiferStatus(l2);
                this.shouldScheduleFluidUpdate = !aquifer$fluidstatus1.equals(aquifer$fluidstatus2);
            } else {
                this.shouldScheduleFluidUpdate = false;
            }
            return blockstate;
        } else if (blockstate.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return blockstate;
        } else {
            MutableDouble mutabledouble = new MutableDouble(Double.NaN);
            Aquifer.FluidStatus aquifer$fluidstatus3 = this.getAquiferStatus(l2);
            double d2 = d1 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus3);
            if (substance + d2 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                Aquifer.FluidStatus aquifer$fluidstatus4 = this.getAquiferStatus(i3);
                double d0 = similarity(k1, i2);
                if (d0 > 0.0) {
                    double d3 = d1 * d0 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1, aquifer$fluidstatus4);
                    if (substance + d3 > 0.0) {
                        this.shouldScheduleFluidUpdate = false;
                        return null;
                    }
                }

                double d4 = similarity(l1, i2);
                if (d4 > 0.0) {
                    double d5 = d1 * d4 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus3, aquifer$fluidstatus4);
                    if (substance + d5 > 0.0) {
                        this.shouldScheduleFluidUpdate = false;
                        return null;
                    }
                }

                boolean flag2 = !aquifer$fluidstatus1.equals(aquifer$fluidstatus3);
                boolean flag = d4 >= FLOWING_UPDATE_SIMULARITY && !aquifer$fluidstatus3.equals(aquifer$fluidstatus4);
                boolean flag1 = d0 >= FLOWING_UPDATE_SIMULARITY && !aquifer$fluidstatus1.equals(aquifer$fluidstatus4);
                if (!flag2 && !flag && !flag1) {
                    this.shouldScheduleFluidUpdate = d0 >= FLOWING_UPDATE_SIMULARITY
                            && similarity(k1, j2) >= FLOWING_UPDATE_SIMULARITY
                            && !aquifer$fluidstatus1.equals(this.getAquiferStatus(j3));
                } else {
                    this.shouldScheduleFluidUpdate = true;
                }
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
        int k1 = Integer.MAX_VALUE, l1 = Integer.MAX_VALUE, i2 = Integer.MAX_VALUE, j2 = Integer.MAX_VALUE;
        long k2 = 0L, l2 = 0L, i3 = 0L, j3 = 0L;
        for (int n = 0; n < 12; n++) {
            int dz = cz[n] - k;
            int j6 = part[n] + dz * dz;
            if (k1 >= j6) {
                j3 = i3;
                i3 = l2;
                l2 = k2;
                k2 = pos[n];
                j2 = i2;
                i2 = l1;
                l1 = k1;
                k1 = j6;
            } else if (l1 >= j6) {
                j3 = i3;
                i3 = l2;
                l2 = pos[n];
                j2 = i2;
                i2 = l1;
                l1 = j6;
            } else if (i2 >= j6) {
                j3 = i3;
                i3 = pos[n];
                j2 = i2;
                i2 = j6;
            } else if (j2 >= j6) {
                j3 = pos[n];
                j2 = j6;
            }
        }
        this.polycore$d1 = k1;
        this.polycore$d2 = l1;
        this.polycore$d3 = i2;
        this.polycore$d4 = j2;
        this.polycore$p1 = k2;
        this.polycore$p2 = l2;
        this.polycore$p3 = i3;
        this.polycore$p4 = j3;
    }
}
