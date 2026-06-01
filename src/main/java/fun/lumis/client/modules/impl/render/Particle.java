package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventAttackEntity;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Particle extends Module {

    public static Particle INSTANCE = new Particle();
    private static final Identifier STAR_TEXTURE = Identifier.of("lumis", "textures/particle/star.png");
    private static final Identifier HEART_TEXTURE = Identifier.of("lumis", "textures/particle/heart.png");
    private static final Identifier DOLLAR_TEXTURE = Identifier.of("lumis", "textures/particle/dollar.png");
    private static final Identifier BLOOM_TEXTURE = Identifier.of("lumis", "textures/particle/bloom.png");
    private static final Identifier SPARKLE_TEXTURE = Identifier.of("lumis", "textures/particle/sparkle.png");

    private final ModeSetting type = new ModeSetting("Тип частиц", "Звездочки", "Звездочки",
            "Сердечки", "Доллары", "Блум", "Сияние");

    private final ListSetting reason = new ListSetting("Добавлять при",
            new BooleanSetting("Бездействии", false),
            new BooleanSetting("Беге", false),
            new BooleanSetting("Ударе", true),
            new BooleanSetting("Падении перла", false),
            new BooleanSetting("Падении трезубца", false),
            new BooleanSetting("Сносе тотема", true));

    private final FloatSetting count = new FloatSetting("Количество", 10f, 2f, 40f, 1);

    private final BooleanSetting glow = new BooleanSetting("Свечение", true);

    private final ArrayList<ParticleData> particles = new ArrayList<>();
    private final Random rnd = new Random();

    public Particle() {
        super("Particles", "Красивые партиклы при разных действиях", ModuleCategory.RENDER);
        addSettings(type, reason, count, glow);
    }

    @Override
    public void onDisable() {
        particles.clear();
        super.onDisable();
    }

    private Identifier getTexture() {
        return switch (type.getIndex()) {
            case 1 -> HEART_TEXTURE;
            case 2 -> DOLLAR_TEXTURE;
            case 3 -> BLOOM_TEXTURE;
            case 4 -> SPARKLE_TEXTURE;
            default -> STAR_TEXTURE;
        };
    }

    private boolean isPositionInBlock(Vec3d position) {
        if (mc.world == null || mc.player == null) return true;
        BlockPos blockPos = BlockPos.ofFloored(position);
        if (mc.world.getBlockState(blockPos).isSolidBlock(mc.world, blockPos)) {
            return true;
        }
        RaycastContext context = new RaycastContext(
                new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ()),
                position,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult result = mc.world.raycast(context);
        return result.getType() == HitResult.Type.BLOCK;
    }

    private float random(float min, float max) {
        return min + rnd.nextFloat() * (max - min);
    }

    private boolean isMoving() {
        return mc.player != null && (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0);
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (mc.player == null || mc.world == null) return;
        if (reason.is("Ударе")) {

            Entity target = event.getTarget();
            if (target != null) {
                for (int i = 0; i < 35; i++) {
                    double targetX = target.getX() + random(-0.4f, 0.4f);
                    double targetY = target.getY() + random(-0.4f, (float) target.getHeight() + 0.4f);
                    double targetZ = target.getZ() + random(-0.4f, 0.4f);

                    if (isPositionInBlock(new Vec3d(targetX, targetY, targetZ))) continue;

                    float baseMx = random(-0.8f, 0.8f) * 2.0f;
                    float baseMy = random(-0.25f, 1.4f);
                    float baseMz = random(-0.8f, 0.8f) * 2.0f;

                    Vec3d velocity = new Vec3d(baseMx * 0.075f, baseMy * 0.075f, baseMz * 0.075f);
                    long life = (long) random(1000, 1200);

                    addParticle(targetX, targetY, targetZ, velocity, ColorUtils.getThemeColor(), 0.3f, life, 0.5f, 0.0007f);
                }
            }
        }
    }

    @EventLink
    public void onPacket(EventPacket e) {
        if (mc.world == null || mc.player == null) return;
        if (!reason.is("Сносе тотема")) return;

        if (e.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) {
                Entity entity = packet.getEntity(mc.world);
                if (entity != null) {
                    double centerX = entity.getX();
                    double centerY = entity.getY() + entity.getHeight() / 2.0;
                    double centerZ = entity.getZ();

                    for (int i = 0; i < 50; i++) {
                        double theta = rnd.nextDouble() * 2.0 * Math.PI;
                        double phi = rnd.nextDouble() * Math.PI;
                        double speed = (rnd.nextDouble() * 0.5 + 0.5) * 0.1;

                        double vx = Math.sin(phi) * Math.cos(theta) * speed;
                        double vy = Math.sin(phi) * Math.sin(theta) * speed;
                        double vz = Math.cos(phi) * speed;

                        double spawnX = centerX + random(-0.3f, 0.3f);
                        double spawnY = centerY + random(-0.3f, 0.3f);
                        double spawnZ = centerZ + random(-0.3f, 0.3f);

                        if (isPositionInBlock(new Vec3d(spawnX, spawnY, spawnZ))) continue;

                        int color = rnd.nextDouble() < 0.7 ? 0xFF00FF00 : 0xFFFFFF00;
                        long life = (long) random(1500, 2000);

                        addParticle(spawnX, spawnY, spawnZ, new Vec3d(vx, vy, vz), color, 0.3f, life, 2.0f, 0.00005f);
                    }
                }
            }
        }
    }

    @EventLink
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        int particleCount = (int) count.get();

        if (reason.is("Бездействии")) {
            Vec3d base = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getHeight() / 2.0, mc.player.getZ());

            for (int i = 0; i < particleCount; i++) {
                double distance = random(7, 35);
                double angle = Math.toRadians(random(0, 360));
                double height = random(-7, 25);

                double spawnX = base.x + Math.cos(angle) * distance;
                double spawnY = base.y + height;
                double spawnZ = base.z + Math.sin(angle) * distance;

                Vec3d spawnPos = new Vec3d(spawnX, spawnY, spawnZ);
                if (isPositionInBlock(spawnPos)) continue;

                long life = (long) random(1500, 2000);
                double speed = rnd.nextDouble() < 0.8 ? random(0.015f, 0.03f) : 0.125f;
                double phi = Math.toRadians(random(0, 360));

                Vec3d velocity = new Vec3d(
                        Math.cos(phi) * speed,
                        random((float) (-speed * 0.1f), (float) (speed * 0.1f)),
                        Math.sin(phi) * speed
                );

                addParticle(spawnX, spawnY, spawnZ, velocity, ColorUtils.getThemeColor(), 0.3f, life, 3.0f, 0.00005f);
            }
        }

        if (reason.is("Беге") && isMoving()) {
            Vec3d motion = mc.player.getVelocity();
            double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            Vec3d direction;
            if (speed < 0.01) {
                direction = mc.player.getRotationVector().multiply(-1);
            } else if (mc.player.isGliding()) {
                direction = motion.normalize().multiply(-1);
            } else {
                direction = new Vec3d(-motion.x / speed, 0, -motion.z / speed);
            }

            double distanceBehind = (mc.player.isGliding() ? 1.2 : 0.5) + (speed > 0.1 ? speed * 1.5 : 0);
            double offsetX = random(-0.35f, 0.35f);
            double offsetZ = random(-0.35f, 0.35f);

            double posX = mc.player.getX() + direction.x * distanceBehind + offsetX;
            double posY = mc.player.isGliding()
                    ? mc.player.getY() + mc.player.getHeight() / 2.0 + direction.y * distanceBehind + random(-0.35f, 0.35f)
                    : mc.player.getY() + random(0.2f, (float) mc.player.getHeight() + 0.1f);
            double posZ = mc.player.getZ() + direction.z * distanceBehind + offsetZ;

            if (!isPositionInBlock(new Vec3d(posX, posY, posZ))) {
                double baseSpeed = 0.075;
                Vec3d velocity = direction.multiply(baseSpeed).add(
                        random(-0.01f, 0.01f),
                        random(-0.05f, 0.01f),
                        random(-0.01f, 0.01f)
                ).multiply(0.1);

                long life = (long) random(1500, 2000);
                addParticle(posX, posY, posZ, velocity, ColorUtils.getThemeColor(), 0.3f, life, 3.0f, 0.00005f);
            }
        }

        boolean trackPearls = reason.is("Падении перла");
        boolean trackTridents = reason.is("Падении трезубца");
        if (trackPearls || trackTridents) {
            Box searchBox = mc.player.getBoundingBox().expand(100);
            List<Entity> entities = mc.world.getOtherEntities(null, searchBox, e2 -> true);

            for (Entity entity : entities) {
                if (trackPearls && entity instanceof EnderPearlEntity pearl) {
                    if (!pearl.isOnGround()) {
                        createProjectileParticles(pearl.getPos(), 1);
                    }
                }

                if (trackTridents && entity instanceof TridentEntity trident) {
                    if (trident.getVelocity().lengthSquared() > 0.01) {
                        createProjectileParticles(trident.getPos(), 1);
                    }
                }
            }
        }
    }

    private void createProjectileParticles(Vec3d position, int cnt) {
        int particleColor = ColorUtils.getThemeColor();

        for (int i = 0; i < cnt * 2.5; i++) {
            double dy = random(0.1f, 0.35f);
            Vec3d particlePos = new Vec3d(position.x, position.y + dy, position.z);

            if (isPositionInBlock(particlePos)) continue;

            float speedMin = random(0.015f, 0.0375f);
            float speedMax = random(0.05f, 0.075f);
            double speedFinal = random(speedMin, speedMax);
            double speedFinalY = speedFinal * 0.4;

            double angleVel = Math.toRadians(random(0, 360));

            Vec3d velocity = new Vec3d(
                    Math.cos(angleVel) * speedFinal,
                    random((float) -speedFinalY, (float) speedFinalY),
                    Math.sin(angleVel) * speedFinal
            );

            long life = (long) random(2400, 2800);
            addParticle(particlePos.x, particlePos.y, particlePos.z, velocity, particleColor, 0.25f, life, 2.0f, 0.00005f);
        }
    }

    private void addParticle(double x, double y, double z, Vec3d velocity, int color, float size, long lifeTime, float smooth, double gravity) {
        if (ParticleData.checkCollision(x, y, z, size, mc)) {
            synchronized (particles) {
                particles.add(new ParticleData(new Vec3d(x, y, z), velocity, color, size, lifeTime, smooth, gravity));
            }
        }
    }

    @EventLink
    public void onRender3D(Event3DRender e) {
        if (mc.player == null || mc.world == null) return;

        synchronized (particles) {
            particles.removeIf(ParticleData::isDead);
        }

        if (particles.isEmpty()) return;

        MatrixStack matrices = e.getMatrices();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Identifier texture = getTexture();

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        if (glow.isState()) {
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        ArrayList<ParticleData> renderList;
        synchronized (particles) {
            renderList = new ArrayList<>(particles);
        }

        for (ParticleData particle : renderList) {
            particle.update(mc);

            double x = particle.position.x - camera.x;
            double y = particle.position.y - camera.y;
            double z = particle.position.z - camera.z;

            matrices.push();
            matrices.translate((float) x, (float) y, (float) z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float half = particle.size / 2f;
            int alpha = (int) (particle.alpha * 255);
            int r = (particle.color >> 16) & 0xFF;
            int g = (particle.color >> 8) & 0xFF;
            int b = particle.color & 0xFF;

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(r, g, b, alpha);
            buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(r, g, b, alpha);
            buffer.vertex(matrix, half, half, 0).texture(1, 0).color(r, g, b, alpha);
            buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(r, g, b, alpha);

            BufferRenderer.drawWithGlobalProgram(buffer.end());

            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    static class ParticleData {
        Vec3d position;
        Vec3d velocity;
        int color;
        float size;
        long lifeTime;
        long birthTime;
        float alpha = 1.0f;
        float smoothFactor;
        long lastUpdateNs;
        double gravity;

        ParticleData(Vec3d position, Vec3d velocity, int color, float size, long lifeTime, float smooth, double gravity) {
            this.position = position;
            this.velocity = velocity;
            this.color = color;
            this.size = size;
            this.lifeTime = lifeTime;
            this.birthTime = System.currentTimeMillis();
            this.lastUpdateNs = System.nanoTime();
            this.smoothFactor = smooth;
            this.gravity = gravity;
        }

        boolean isDead() {
            return System.currentTimeMillis() - birthTime >= lifeTime;
        }

        void update(net.minecraft.client.MinecraftClient mc) {
            long nowNs = System.nanoTime();
            double deltaSec = (nowNs - lastUpdateNs) / 1_000_000_000.0;
            lastUpdateNs = nowNs;

            float progress = Math.min(1.0f, (float) (System.currentTimeMillis() - birthTime) / lifeTime);
            double factor = Math.pow(1.0 - progress, smoothFactor);

            double vx = velocity.x;
            double vy = velocity.y;
            double vz = velocity.z;

            double newX = position.x;
            double newY = position.y;
            double newZ = position.z;

            newX += vx * factor * (deltaSec * 60);
            if (!checkCollision(newX, position.y, position.z, size, mc)) {
                vx = -vx * 0.8;
                newX = position.x;
            }

            newY += vy * factor * (deltaSec * 60);
            if (!checkCollision(newX, newY, position.z, size, mc)) {
                vy = -vy * 1.5;
                newY = position.y;
            }

            newZ += vz * factor * (deltaSec * 60);
            if (!checkCollision(newX, newY, newZ, size, mc)) {
                vz = -vz * 0.8;
                newZ = position.z;
            }

            position = new Vec3d(newX, newY, newZ);
            velocity = new Vec3d(vx * 0.9999, vy * 0.9999 - gravity, vz * 0.9999);
            alpha = 1.0f - progress;
        }

        static boolean checkCollision(double x, double y, double z, float size, net.minecraft.client.MinecraftClient mc) {
            if (mc.world == null) return false;
            double half = size * 0.5;
            int minX = MathHelper.floor(x - half);
            int maxX = MathHelper.floor(x + half);
            int minY = MathHelper.floor(y - half);
            int maxY = MathHelper.floor(y + half);
            int minZ = MathHelper.floor(z - half);
            int maxZ = MathHelper.floor(z + half);

            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int bx = minX; bx <= maxX; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        pos.set(bx, by, bz);
                        BlockState state = mc.world.getBlockState(pos);
                        if (!state.isAir() && state.isSolidBlock(mc.world, pos)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}

