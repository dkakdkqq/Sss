package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.Optional;

public class Trajectories extends Module {
    public static Trajectories INSTANCE = new Trajectories();

    private static final int MAX_STEPS = 440;
    private static final double SIMULATION_STEP = 0.5;
    private static final double SPLASH_RADIUS = 4.0;
    private static final Identifier GLOW_TEXTURE = Identifier.of("lumis", "textures/trajectories/glow.png");

    private final FloatSetting lineWidth = new FloatSetting("Ширина линии", 2.2f, 0.5f, 5.0f, 0.1f);

    public Trajectories() {
        super("Trajectories", "Показывает траекторию предмета в руке", ModuleCategory.RENDER);
        addSettings(lineWidth);
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = getHeldProjectileStack();
        if (stack.isEmpty()) return;

        ProjectileParams params = getParams(stack);
        if (params == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d startPos = mc.player.getCameraPosVec(tickDelta);
        Vec3d[] directions = getShotDirections(stack, tickDelta);
        PredictionResult[] results = new PredictionResult[directions.length];
        int resultCount = 0;
        for (Vec3d direction : directions) {
            PredictionResult result = predict(mc.player, params, startPos, direction);
            if (result != null && result.points.length >= 2) {
                results[resultCount++] = result;
            }
        }
        if (resultCount == 0) return;

        MatrixStack matrices = event.getMatrices();
        Camera camera = event.getCamera();
        Vec3d cameraPos = camera.getPos();
        int themeColor = ColorUtils.getThemeColor();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(lineWidth.getValue().floatValue());

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < resultCount; i++) {
            PredictionResult result = results[i];

            drawTrajectoryLine(matrix, result.points, ColorUtils.setAlphaColor(themeColor, 190));

            if (result.entityHit != null && result.entityHit.isAlive()) {
                drawEntityBox(matrix, result.entityHit, ColorUtils.rgba(255, 70, 70, 210));
            } else if (result.blockHit != null) {
                drawImpactMarker(matrix, result.hitPos, result.blockHit.getSide(), ColorUtils.setAlphaColor(themeColor, 230));
            }

            if (stack.isOf(Items.SPLASH_POTION) && result.hitPos != null) {
                drawPotionRadiusGlow(matrices, result.hitPos, themeColor);
            }
        }

        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private ItemStack getHeldProjectileStack() {
        ItemStack main = mc.player.getMainHandStack();
        if (!main.isEmpty() && getParams(main) != null) return main;
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty() && getParams(off) != null) return off;
        return ItemStack.EMPTY;
    }

    private ProjectileParams getParams(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG) {
            return new ProjectileParams(1.5, 0.03, 0.99);
        }
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            return new ProjectileParams(0.5, 0.05, 0.99);
        }
        if (item instanceof BowItem) {
            float power = 1.0f;
            if (mc.player.isUsingItem() && mc.player.getActiveItem() == stack) {
                float use = mc.player.getItemUseTime();
                float f = use / 20.0f;
                f = (f * f + f * 2.0f) / 3.0f;
                power = Math.min(f, 1.0f);
            }
            double velocity = 3.0 * power;
            return velocity <= 0.01 ? null : new ProjectileParams(velocity, 0.05, 0.99);
        }
        if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) return null;
            return new ProjectileParams(3.15, 0.05, 0.99);
        }
        if (item instanceof TridentItem) {
            return new ProjectileParams(2.5, 0.05, 0.99);
        }
        return null;
    }

    private Vec3d[] getShotDirections(ItemStack stack, float tickDelta) {
        Vec3d baseDir = mc.player.getRotationVec(tickDelta).normalize();
        if (!(stack.getItem() instanceof CrossbowItem) || InventoryUtils.getEnchantmentLevel(stack, Enchantments.MULTISHOT) <= 0) {
            return new Vec3d[]{baseDir};
        }

        float baseYaw = (float) (MathHelper.atan2(baseDir.z, baseDir.x) * (180.0 / Math.PI)) - 90.0f;
        float basePitch = (float) -(MathHelper.atan2(baseDir.y, MathHelper.sqrt((float) (baseDir.x * baseDir.x + baseDir.z * baseDir.z))) * (180.0 / Math.PI));
        return new Vec3d[]{
                getDirectionFromYawPitch(baseYaw - 10.0f, basePitch),
                baseDir,
                getDirectionFromYawPitch(baseYaw + 10.0f, basePitch)
        };
    }

    private Vec3d getDirectionFromYawPitch(float yawDeg, float pitchDeg) {
        float yaw = yawDeg * ((float) Math.PI / 180.0f);
        float pitch = pitchDeg * ((float) Math.PI / 180.0f);
        float x = MathHelper.sin(-yaw - (float) Math.PI) * -MathHelper.cos(-pitch);
        float y = MathHelper.sin(-pitch);
        float z = MathHelper.cos(-yaw - (float) Math.PI) * -MathHelper.cos(-pitch);
        return new Vec3d(x, y, z).normalize();
    }

    private PredictionResult predict(PlayerEntity player, ProjectileParams params, Vec3d startPos, Vec3d direction) {
        Vec3d pos = startPos;
        Vec3d motion = direction.normalize().multiply(params.velocity);
        Vec3d[] points = new Vec3d[MAX_STEPS + 1];
        int count = 0;
        points[count++] = pos;

        Entity entityHit = null;
        Vec3d entityHitPos = null;

        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3d prev = pos;
            Vec3d next = pos.add(motion.multiply(SIMULATION_STEP));

            if (entityHit == null) {
                EntityHit hit = rayTraceEntities(prev, next, player);
                if (hit != null) {
                    entityHit = hit.entity;
                    entityHitPos = hit.hitPos;
                }
            }

            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                    prev,
                    next,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                points[count++] = blockHit.getPos();
                return new PredictionResult(copyPoints(points, count), blockHit, blockHit.getPos(), entityHit, entityHitPos);
            }

            points[count++] = next;
            pos = next;

            boolean inWater = mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isOf(net.minecraft.block.Blocks.WATER);
            double drag = Math.pow(inWater ? 0.8 : params.drag, SIMULATION_STEP);
            motion = motion.multiply(drag).subtract(0.0, params.gravity * SIMULATION_STEP, 0.0);
            if (pos.y <= mc.world.getBottomY()) break;
        }

        Vec3d hitPos = entityHitPos != null ? entityHitPos : points[count - 1];
        return new PredictionResult(copyPoints(points, count), null, hitPos, entityHit, entityHitPos);
    }

    private Vec3d[] copyPoints(Vec3d[] points, int count) {
        Vec3d[] out = new Vec3d[count];
        System.arraycopy(points, 0, out, 0, count);
        return out;
    }

    private EntityHit rayTraceEntities(Vec3d from, Vec3d to, Entity owner) {
        Box search = new Box(from, to).expand(1.0);
        Entity closest = null;
        Vec3d closestHit = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getOtherEntities(owner, search, entity -> entity != null && entity.isAlive() && entity.canHit())) {
            Optional<Vec3d> hit = entity.getBoundingBox().expand(0.3).raycast(from, to);
            if (hit.isEmpty()) continue;

            double distance = from.squaredDistanceTo(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = entity;
                closestHit = hit.get();
            }
        }

        return closest == null ? null : new EntityHit(closest, closestHit);
    }

    private void drawTrajectoryLine(Matrix4f matrix, Vec3d[] points, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d start = points[i];
            Vec3d end = points[i + 1];
            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a);
            buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawImpactMarker(Matrix4f matrix, Vec3d pos, Direction side, int color) {
        Vec3d normal = Vec3d.of(side.getVector()).normalize();
        Vec3d u = side == Direction.UP || side == Direction.DOWN
                ? new Vec3d(1, 0, 0)
                : normal.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d v = normal.crossProduct(u).normalize();
        Vec3d center = pos.add(normal.multiply(0.004));
        double radius = 0.35;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        int segments = 48;
        Vec3d previous = null;
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            Vec3d point = center.add(u.multiply(Math.cos(angle) * radius)).add(v.multiply(Math.sin(angle) * radius));
            if (previous != null) {
                buffer.vertex(matrix, (float) previous.x, (float) previous.y, (float) previous.z).color(r, g, b, a);
                buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z).color(r, g, b, a);
            }
            previous = point;
        }

        Vec3d left = center.add(u.multiply(-radius));
        Vec3d right = center.add(u.multiply(radius));
        Vec3d down = center.add(v.multiply(-radius));
        Vec3d up = center.add(v.multiply(radius));
        buffer.vertex(matrix, (float) left.x, (float) left.y, (float) left.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) right.x, (float) right.y, (float) right.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) down.x, (float) down.y, (float) down.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) up.x, (float) up.y, (float) up.z).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawEntityBox(Matrix4f matrix, Entity entity, int color) {
        Box box = entity.getBoundingBox();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        vertexBox(buffer, matrix, box, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void vertexBox(BufferBuilder buffer, Matrix4f matrix, Box box, int r, int g, int b, int a) {
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    private void drawPotionRadiusGlow(MatrixStack matrices, Vec3d pos, int themeColor) {
        int color = ColorUtils.setAlphaColor(themeColor, 82);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        float radius = (float) SPLASH_RADIUS;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        matrices.push();
        matrices.translate(pos.x, pos.y + 0.012, pos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -radius, 0, -radius).texture(0, 0).color(r, g, b, a);
        buffer.vertex(matrix, -radius, 0, radius).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, radius, 0, radius).texture(1, 1).color(r, g, b, a);
        buffer.vertex(matrix, radius, 0, -radius).texture(1, 0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    private record ProjectileParams(double velocity, double gravity, double drag) {
    }

    private record EntityHit(Entity entity, Vec3d hitPos) {
    }

    private record PredictionResult(Vec3d[] points, BlockHitResult blockHit, Vec3d hitPos, Entity entityHit, Vec3d entityHitPos) {
    }
}
