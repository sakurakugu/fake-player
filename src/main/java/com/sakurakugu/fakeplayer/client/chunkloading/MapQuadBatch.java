package com.sakurakugu.fakeplayer.client.chunkloading;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * 把地图上同类的一批矩形合并成单个 {@link GuiElementRenderState}。
 *
 * <p>{@code GuiRenderState.findAppropriateNode} 每加入一个元素都会线性扫描同层已提交的元素
 * （{@code hasIntersection} 从下标 0 开始，且 {@code ScreenRectangle.intersects} 用严格不等号，
 * 相邻格子互不相交），所以逐区块提交会让每帧开销变成元素数的平方——
 * 地图卡顿的主因是元素个数（与分辨率、可见区块数成正比），不是 draw call 或四边形数量。
 * 合并之后每帧只有常数个元素，开销与分辨率、缩放、可见区块数全部解耦。
 *
 * <p>一个实例就是一帧的一个元素：{@link #begin} 捕获管线、贴图、姿态与裁剪区，
 * 之后只往自己的数组里追加矩形，渲染时由 {@link #buildVertices} 一次性输出全部四边形。
 */
final class MapQuadBatch implements GuiElementRenderState {
    private RenderPipeline pipeline;
    private TextureSetup textureSetup;
    private Matrix3x2fc pose = new Matrix3x2f();
    private ScreenRectangle scissorArea;
    private ScreenRectangle bounds = ScreenRectangle.empty();
    private int[] rects = new int[4 * 512];
    private int[] colors = new int[512];
    private float[] uvs = new float[4 * 512];
    private int count;
    private boolean textured;

    /**
     * 开始收集这一帧的矩形。
     *
     * @param textured 是否需要 UV（地形底图用带贴图的管线）
     */
    void begin(boolean textured, RenderPipeline pipeline, TextureSetup textureSetup,
               GuiGraphicsExtractor graphics, int width, int height) {
        this.textured = textured;
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        // 姿态在提取期会被修改，必须拷贝一份
        this.pose = new Matrix3x2f(graphics.pose());
        this.scissorArea = graphics.peekScissorStack();
        this.bounds = new ScreenRectangle(0, 0, width, height);
        this.count = 0;
    }

    boolean isEmpty() {
        return count == 0;
    }

    void addRect(int x0, int y0, int x1, int y1, int color) {
        if (x1 <= x0 || y1 <= y0) return;
        ensureCapacity(count + 1);
        int base = count * 4;
        rects[base] = x0;
        rects[base + 1] = y0;
        rects[base + 2] = x1;
        rects[base + 3] = y1;
        colors[count] = color;
        count++;
    }

    void addTexturedRect(int x0, int y0, int x1, int y1, float u0, float v0, float u1, float v1, int color) {
        if (x1 <= x0 || y1 <= y0) return;
        ensureCapacity(count + 1);
        int base = count * 4;
        rects[base] = x0;
        rects[base + 1] = y0;
        rects[base + 2] = x1;
        rects[base + 3] = y1;
        uvs[base] = u0;
        uvs[base + 1] = v0;
        uvs[base + 2] = u1;
        uvs[base + 3] = v1;
        colors[count] = color;
        count++;
    }

    private void ensureCapacity(int needed) {
        if (needed <= colors.length) return;
        int size = Math.max(needed, colors.length * 2);
        rects = java.util.Arrays.copyOf(rects, size * 4);
        colors = java.util.Arrays.copyOf(colors, size);
        uvs = java.util.Arrays.copyOf(uvs, size * 4);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int index = 0; index < count; index++) {
            int base = index * 4;
            int x0 = rects[base];
            int y0 = rects[base + 1];
            int x1 = rects[base + 2];
            int y1 = rects[base + 3];
            int color = colors[index];
            if (textured) {
                float u0 = uvs[base];
                float v0 = uvs[base + 1];
                float u1 = uvs[base + 2];
                float v1 = uvs[base + 3];
                consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
                consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
            } else {
                consumer.addVertexWith2DPose(pose, x0, y0).setColor(color);
                consumer.addVertexWith2DPose(pose, x0, y1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y0).setColor(color);
            }
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
