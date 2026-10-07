package kr.majeong.blockaimbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class BlockAimClient implements ClientModInitializer {
    public static AimConfig config;
    private KeyMapping trigger;
    private BlockPos locked;
    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private int scanCooldown;
    private double speedFactor = 1;

    @Override public void onInitializeClient() {
        config = AimConfig.load();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("blockaimbot", "controls"));
        trigger = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blockaimbot.trigger",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("block").requires(FabricClientCommandSource::attended)
                .executes(ctx -> select(ctx.getSource()))
                .then(literal("clear").executes(ctx -> {
                    config.targetBlock = "";
                    locked = null;
                    boolean saved = config.save();
                    ctx.getSource().sendFeedback(Component.translatable(saved ? "blockaimbot.cleared" : "blockaimbot.save_failed"));
                    return saved ? 1 : 0;
                }))
                .then(literal("settings").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new ConfigScreen(null)));
                    return 1;
                }))));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private int select(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendError(Component.translatable("blockaimbot.look_at_block"));
            return 0;
        }
        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        config.targetBlock = BuiltInRegistries.BLOCK.getKey(block).toString();
        locked = null;
        scanCooldown = 0;
        source.sendFeedback(Component.translatable("blockaimbot.selected", config.targetBlock));
        if (!config.save()) source.sendError(Component.translatable("blockaimbot.save_failed"));
        return 1;
    }

    private void tick(Minecraft mc) {
        if (lastLevel != mc.level) { lastLevel = mc.level; reset(); }
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || !mc.isWindowActive()
                || !mc.player.isAlive() || !trigger.isDown() || config.targetBlock.isEmpty()) {
            reset();
            return;
        }
        Identifier id = Identifier.tryParse(config.targetBlock);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { reset(); return; }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        Vec3 eye = mc.player.getEyePosition();
        if (locked == null && scanCooldown > 0) { scanCooldown--; return; }
        Vec3 aim = locked == null ? null : visiblePoint(mc, locked, eye, block);
        // Scan at most 5 times/sec; immediately rescan when the lock is invalid.
        if (aim == null || --scanCooldown <= 0) {
            BlockPos next = nearest(mc, eye, block);
            if (next == null) { locked = null; scanCooldown = 4; return; }
            if (!next.equals(locked)) {
                speedFactor = 1 + ThreadLocalRandom.current().nextDouble(-1, 1) * config.randomization / 100;
            }
            locked = next;
            scanCooldown = 4;
            aim = visiblePoint(mc, locked, eye, block);
        }
        if (aim == null) return;
        Vec3 delta = aim.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        float[] angles = AimMath.step(mc.player.getYRot(), mc.player.getXRot(), yaw, pitch,
                config.aimSpeed * speedFactor / 20);
        mc.player.setYRot(angles[0]);
        mc.player.setXRot(angles[1]);
    }

    private void reset() { locked = null; scanCooldown = 0; speedFactor = 1; }

    private BlockPos nearest(Minecraft mc, Vec3 eye, Block target) {
        int r = (int) Math.ceil(config.maxDistance);
        BlockPos origin = BlockPos.containing(eye);
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r,-r,-r), origin.offset(r,r,r))) {
            if (!mc.level.hasChunkAt(pos)) continue;
            if (Vec3.atCenterOf(pos).distanceToSqr(eye) > config.maxDistance * config.maxDistance) continue;
            if (mc.level.getBlockState(pos).is(target)) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        for (BlockPos pos : candidates) if (visiblePoint(mc, pos, eye, target) != null) return pos;
        return null;
    }

    /** Ray test actual outline shapes: slabs and other partial blocks remain targetable. */
    private Vec3 visiblePoint(Minecraft mc, BlockPos pos, Vec3 eye, Block target) {
        if (!mc.level.hasChunkAt(pos) || !mc.level.getBlockState(pos).is(target)
                || Vec3.atCenterOf(pos).distanceToSqr(eye) > config.maxDistance * config.maxDistance) return null;
        var shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        for (var box : shape.toAabbs()) {
            Vec3 center = new Vec3(pos.getX() + (box.minX + box.maxX) / 2,
                    pos.getY() + (box.minY + box.maxY) / 2, pos.getZ() + (box.minZ + box.maxZ) / 2);
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return center;
        }
        return null;
    }
}
