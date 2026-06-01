package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.ShaderUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class BlockOverlay extends Module {

    public static BlockOverlay INSTANCE = new BlockOverlay();
    private final ModeSetting mode = new ModeSetting("Режим", "Шейдер", "Шейдер", "Нитки");
    private final FloatSetting waveSpeed = new FloatSetting("Скорость волн", 1.2f, 0.1f, 5.0f, 0.1f)
            .visible(() -> mode.is("Шейдер"));
    private final FloatSetting waveScale = new FloatSetting("Частота волн", 1.0f, 1.0f, 3.0f, 0.1f)
            .visible(() -> mode.is("Шейдер"));
    private final FloatSetting lineSpeed = new FloatSetting("Скорость нитей", 1.4f, 0.1f, 5.0f, 0.1f)
            .visible(() -> mode.is("Нитки"));
    private final FloatSetting lineJitter = new FloatSetting("Изгиб нитей", 0.55f, 0.0f, 1.5f, 0.01f)
            .visible(() -> mode.is("Нитки"));
    private final FloatSetting outline = new FloatSetting("Ширина обводки", 1.1f, 0.1f, 5.0f, 0.1f);
    private final FloatSetting glow = new FloatSetting("Сила свечения", 1.0f, 0.0f, 5.0f, 0.1f);
    private final FloatSetting fill = new FloatSetting("Заливка", 0.6f, 0.0f, 1.0f, 0.01f);
    private final FloatSetting alpha = new FloatSetting("Прозрачность", 1.0f, 0.0f, 1.0f, 0.01f);
    private final FloatSetting smooth = new FloatSetting("Плавность", 0.24f, 0.05f, 0.6f, 0.01f);

    private Framebuffer maskBuffer;
    private int fbWidth = -1;
    private int fbHeight = -1;
    private boolean hasMask;

    private BlockPos lastBlockPos;
    private Box displayBox;
    private Box targetBox;
    private int cachedThemeColor1 = 0xFFFFFFFF;
    private int cachedThemeColor2 = 0xFFFFFFFF;

    public BlockOverlay() {
        super("BlockOverlay", "Block overlay shader", ModuleCategory.RENDER);
        addSettings(mode, waveSpeed, waveScale, lineSpeed, lineJitter, outline, glow, fill, alpha, smooth);
    }

    @Override
    public void onDisable() {
        hasMask = false;
        lastBlockPos = null;
        displayBox = null;
        targetBox = null;
        super.onDisable();
    }

    @EventLink(priority = Priority.LOW)
    public void onRender3D(Event3DRender event) {
        if (mc == null || mc.world == null || mc.player == null) return;

        Box worldBox = getTargetedBlockBox();
        if (worldBox == null) {
            hasMask = false;
            lastBlockPos = null;
            displayBox = null;
            targetBox = null;
            return;
        }

        if (displayBox == null || targetBox == null || lastBlockPos == null) {
            displayBox = worldBox;
            targetBox = worldBox;
        } else {
            targetBox = worldBox;
            displayBox = lerpBox(displayBox, targetBox, smooth.get());
        }
        lastBlockPos = BlockPos.ofFloored(worldBox.minX, worldBox.minY, worldBox.minZ);
        updateCachedThemeColors();

        Vec3d cam = event.getCamera().getPos();
        Box localBox = displayBox.offset(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();

        ensureMaskBuffer();
        if (maskBuffer == null) return;

        hasMask = true;
        maskBuffer.setClearColor(0f, 0f, 0f, 0f);
        maskBuffer.clear();
        copyMainDepthToMask();
        maskBuffer.beginWrite(false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        drawMaskBox(matrix, localBox);
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        mc.getFramebuffer().beginWrite(false);

        if (mode.is("Нитки")) {
            drawAnimatedWeb(matrix, localBox);
            return;
        }
    }

    @EventLink(priority = Priority.HIGHEST)
    public void onRender2D(EventRender.Default event) {
        if (!hasMask || maskBuffer == null) return;
        if (mode.is("Нитки")) return;

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.blockOverlay);
        if (shader == null) return;

        boolean lineMode = mode.is("Нитки");
        int color1 = cachedThemeColor1;
        int color2 = cachedThemeColor2;

        mc.getFramebuffer().beginWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        RenderSystem.setShader(ShaderUtils.blockOverlay);
        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());

        setUniform(shader, "texelSize", 1.0f / Math.max(1, mc.getWindow().getFramebufferWidth()), 1.0f / Math.max(1, mc.getWindow().getFramebufferHeight()));
        setUniform(shader, "color", ColorUtils.redf(color1), ColorUtils.greenf(color1), ColorUtils.bluef(color1));
        setUniform(shader, "color2", ColorUtils.redf(color2), ColorUtils.greenf(color2), ColorUtils.bluef(color2));
        setUniform(shader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
        setUniform(shader, "speed", waveSpeed.get());
        setUniform(shader, "scale", waveScale.get());
        setUniform(shader, "outline", outline.get());
        setUniform(shader, "glow", lineMode ? 0.0f : glow.get());
        setUniform(shader, "fill", lineMode ? 0.0f : fill.get());
        setUniform(shader, "alpha", lineMode ? 1.0f : alpha.get());
        setUniform(shader, "outlineOnly", lineMode ? 1.0f : 0.0f);

        drawFullscreenQuad();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, 0);
    }

    private void setUniform(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }

    private void ensureMaskBuffer() {
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (maskBuffer == null || fbWidth != w || fbHeight != h) {
            if (maskBuffer != null) {
                maskBuffer.delete();
            }
            maskBuffer = new SimpleFramebuffer(w, h, true);
            fbWidth = w;
            fbHeight = h;
        }
    }

    private Box getTargetedBlockBox() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (pos == null) return null;
        if (mc.world.getBlockState(pos).isAir()) return null;

        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
        return box.expand(0.002);
    }

    private Box lerpBox(Box a, Box b, float t) {
        return new Box(
                a.minX + (b.minX - a.minX) * t,
                a.minY + (b.minY - a.minY) * t,
                a.minZ + (b.minZ - a.minZ) * t,
                a.maxX + (b.maxX - a.maxX) * t,
                a.maxY + (b.maxY - a.maxY) * t,
                a.maxZ + (b.maxZ - a.maxZ) * t
        );
    }

    private void drawAnimatedWeb(Matrix4f matrix, Box box) {
        int strandsPerFace = 5;
        int samples = 18;
        float t = (System.currentTimeMillis() % 100000L) / 1000.0f * lineSpeed.get();
        float lineWidth = 0.0025f;
        float bendBase = 0.06f + lineJitter.get() * 0.20f;
        int baseAlpha = Math.max(20, Math.min(255, (int) (alpha.get() * 210.0f)));
        int themeColor = cachedThemeColor1;
        long seed = lastBlockPos != null ? lastBlockPos.asLong() : 1L;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        drawFilledBox(matrix, box, ColorUtils.setAlphaColor(themeColor, (int) (alpha.get() * fill.get() * 170.0f)));

        for (int face = 0; face < 6; face++) {
            int[] neighbors = faceNeighbors(face);
            for (int strand = 0; strand < strandsPerFace; strand++) {
                int key = face * 1000 + strand * 53;
                int adj = neighbors[strand % neighbors.length];
                double phase = t * (0.95 + rand01(seed, key + 1) * 0.55) + strand * 0.83 + face * 1.11;
                double edgeT = clamp01(0.5 + Math.sin(phase * 1.37 + rand01(seed, key + 2) * 6.2831853) * 0.38);

                Vec3d pivot = edgePoint(box, face, adj, edgeT, 0.0015);
                Vec3d start = facePoint(box, face,
                        clamp01(0.5 + (rand01(seed, key + 3) - 0.5) * 0.46),
                        clamp01(0.5 + (rand01(seed, key + 4) - 0.5) * 0.46),
                        0.0015);
                Vec3d end = facePoint(box, adj,
                        clamp01(0.5 + (rand01(seed, key + 5) - 0.5) * 0.46),
                        clamp01(0.5 + (rand01(seed, key + 6) - 0.5) * 0.46),
                        0.0015);

                Vec3d[] basisA = faceBasis(face);
                Vec3d[] basisB = faceBasis(adj);
                Vec3d normalA = faceNormal(face);
                Vec3d normalB = faceNormal(adj);
                double bendA = bendBase * (0.7 + rand01(seed, key + 7))
                        * Math.sin(phase * 1.9 + rand01(seed, key + 8) * 6.2831853);
                double bendB = bendBase * (0.7 + rand01(seed, key + 9))
                        * Math.cos(phase * 1.7 + rand01(seed, key + 10) * 6.2831853);

                Vec3d dirA = pivot.subtract(start);
                Vec3d c1a = start.add(dirA.multiply(0.38)).add(basisA[0].multiply(bendA)).add(basisA[1].multiply(-bendA * 0.55));
                Vec3d c2a = start.add(dirA.multiply(0.76)).add(basisA[0].multiply(-bendA * 0.65)).add(basisA[1].multiply(bendA * 0.4));

                Vec3d dirB = end.subtract(pivot);
                Vec3d c1b = pivot.add(dirB.multiply(0.24)).add(basisB[0].multiply(bendB)).add(basisB[1].multiply(bendB * 0.45));
                Vec3d c2b = pivot.add(dirB.multiply(0.62)).add(basisB[0].multiply(-bendB * 0.7)).add(basisB[1].multiply(-bendB * 0.35));

                int alphaLine = Math.max(18, Math.min(255, (int) (baseAlpha * (0.74 + 0.26 * Math.sin(phase * 2.6)))));
                int color = ColorUtils.setAlphaColor(themeColor, alphaLine);
                drawBezierRibbon(matrix, start, c1a, c2a, pivot, normalA, samples, color, lineWidth);
                drawBezierRibbon(matrix, pivot, c1b, c2b, end, normalB, samples, color, lineWidth);
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void copyMainDepthToMask() {
        if (maskBuffer == null) return;

        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, maskBuffer.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
    }

    private Vec3d cubicBezier(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        double it = 1.0 - t;
        double it2 = it * it;
        double t2 = t * t;
        return p0.multiply(it2 * it)
                .add(p1.multiply(3.0 * it2 * t))
                .add(p2.multiply(3.0 * it * t2))
                .add(p3.multiply(t2 * t));
    }

    private void drawBezierRibbon(Matrix4f matrix, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d faceNormal, int samples, int color, float halfWidth) {
        Vec3d[] points = new Vec3d[samples + 1];
        for (int s = 0; s <= samples; s++) {
            float u = (float) s / (float) samples;
            points[s] = cubicBezier(p0, p1, p2, p3, u);
        }

        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < samples; i++) {
            Vec3d a = points[i];
            Vec3d b = points[i + 1];
            Vec3d dir = b.subtract(a);
            if (dir.lengthSquared() < 1.0E-6) continue;

            Vec3d perp = faceNormal.crossProduct(dir).normalize().multiply(halfWidth);
            Vec3d aL = a.add(perp);
            Vec3d aR = a.subtract(perp);
            Vec3d bL = b.add(perp);
            Vec3d bR = b.subtract(perp);

            quads.vertex(matrix, (float) aL.x, (float) aL.y, (float) aL.z).color(color);
            quads.vertex(matrix, (float) aR.x, (float) aR.y, (float) aR.z).color(color);
            quads.vertex(matrix, (float) bR.x, (float) bR.y, (float) bR.z).color(color);
            quads.vertex(matrix, (float) bL.x, (float) bL.y, (float) bL.z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(quads.end());
    }

    private void updateCachedThemeColors() {
        if (Lumis.INSTANCE == null || Lumis.INSTANCE.themeStorage == null || Lumis.INSTANCE.themeStorage.getThemes() == null) {
            cachedThemeColor1 = ColorUtils.getThemeColor(0);
            cachedThemeColor2 = ColorUtils.getThemeColor(180);
            return;
        }

        var theme = Lumis.INSTANCE.themeStorage.getThemes().getTheme();
        if (theme == null) {
            cachedThemeColor1 = ColorUtils.getThemeColor(0);
            cachedThemeColor2 = ColorUtils.getThemeColor(180);
            return;
        }

        if (!"Rainbow".equals(theme.getName())) {
            int base = (theme.color != null && theme.color.length > 0) ? theme.color[0] : ColorUtils.getThemeColor(0);
            cachedThemeColor1 = base;
            cachedThemeColor2 = base;
        } else {
            cachedThemeColor1 = ColorUtils.getThemeColor();
            cachedThemeColor2 = ColorUtils.getThemeColor(180);
        }
    }

    private int[] faceNeighbors(int face) {
        return switch (face) {
            case 0, 1 -> new int[]{2, 3, 4, 5};
            case 2, 3 -> new int[]{0, 1, 4, 5};
            default -> new int[]{0, 1, 2, 3};
        };
    }

    private Vec3d[] faceBasis(int face) {
        return switch (face) {
            case 0, 1 -> new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 0, 1)};
            case 2, 3 -> new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)};
            default -> new Vec3d[]{new Vec3d(0, 0, 1), new Vec3d(0, 1, 0)};
        };
    }

    private Vec3d faceNormal(int face) {
        return switch (face) {
            case 0 -> new Vec3d(0, 1, 0);
            case 1 -> new Vec3d(0, -1, 0);
            case 2 -> new Vec3d(0, 0, -1);
            case 3 -> new Vec3d(0, 0, 1);
            case 4 -> new Vec3d(-1, 0, 0);
            default -> new Vec3d(1, 0, 0);
        };
    }

    private Vec3d edgePoint(Box box, int faceA, int faceB, double t, double inset) {
        double x = Double.NaN;
        double y = Double.NaN;
        double z = Double.NaN;

        double[] fixedA = faceFixedCoords(box, faceA, inset);
        if (!Double.isNaN(fixedA[0])) x = fixedA[0];
        if (!Double.isNaN(fixedA[1])) y = fixedA[1];
        if (!Double.isNaN(fixedA[2])) z = fixedA[2];

        double[] fixedB = faceFixedCoords(box, faceB, inset);
        if (!Double.isNaN(fixedB[0])) x = fixedB[0];
        if (!Double.isNaN(fixedB[1])) y = fixedB[1];
        if (!Double.isNaN(fixedB[2])) z = fixedB[2];

        double tt = clamp01(t);
        if (Double.isNaN(x)) x = lerp(box.minX, box.maxX, tt);
        if (Double.isNaN(y)) y = lerp(box.minY, box.maxY, tt);
        if (Double.isNaN(z)) z = lerp(box.minZ, box.maxZ, tt);
        return new Vec3d(x, y, z);
    }

    private double[] faceFixedCoords(Box box, int face, double inset) {
        return switch (face) {
            case 0 -> new double[]{Double.NaN, box.maxY - inset, Double.NaN};
            case 1 -> new double[]{Double.NaN, box.minY + inset, Double.NaN};
            case 2 -> new double[]{Double.NaN, Double.NaN, box.minZ + inset};
            case 3 -> new double[]{Double.NaN, Double.NaN, box.maxZ - inset};
            case 4 -> new double[]{box.minX + inset, Double.NaN, Double.NaN};
            default -> new double[]{box.maxX - inset, Double.NaN, Double.NaN};
        };
    }

    private Vec3d facePoint(Box box, int face, double u, double v, double inset) {
        u = clamp01(u);
        v = clamp01(v);
        return switch (face) {
            case 0 -> new Vec3d(lerp(box.minX, box.maxX, u), box.maxY - inset, lerp(box.minZ, box.maxZ, v)); // up
            case 1 -> new Vec3d(lerp(box.minX, box.maxX, u), box.minY + inset, lerp(box.minZ, box.maxZ, v)); // down
            case 2 -> new Vec3d(lerp(box.minX, box.maxX, u), lerp(box.minY, box.maxY, v), box.minZ + inset); // north
            case 3 -> new Vec3d(lerp(box.minX, box.maxX, u), lerp(box.minY, box.maxY, v), box.maxZ - inset); // south
            case 4 -> new Vec3d(box.minX + inset, lerp(box.minY, box.maxY, v), lerp(box.minZ, box.maxZ, u)); // west
            default -> new Vec3d(box.maxX - inset, lerp(box.minY, box.maxY, v), lerp(box.minZ, box.maxZ, u)); // east
        };
    }

    private double rand01(long seed, int salt) {
        long x = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return (double) (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void drawFilledBox(Matrix4f matrix, Box box, int color) {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // bottom
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        // top
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);
        // north
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
        // south
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        // west
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        // east
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);

        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private void drawMaskBox(Matrix4f matrix, Box box) {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int white = 0xFFFFFFFF;

        // bottom
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(white);
        // top
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(white);
        // north
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(white);
        // south
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(white);
        // west
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(white);
        // east
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(white);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(white);

        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private void drawFullscreenQuad() {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float width = Math.max(mc.getWindow().getScaledWidth(), 1);
        float height = Math.max(mc.getWindow().getScaledHeight(), 1);
        b.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        b.vertex(0, height, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        b.vertex(width, height, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        b.vertex(width, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }
}

