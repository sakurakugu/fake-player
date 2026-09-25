package com.sakurakugu.fakeplayer.client.chunkloading;

/**
 * 地图的编辑模式。
 *
 * <p>强加载和擦除合成同一个模式：涂什么、擦什么由鼠标按键决定（左键强加载，右键擦除），
 * 免得画几笔就切一次模式。
 */
public enum ChunkMapEditMode {
    BROWSE,
    EDIT
}
