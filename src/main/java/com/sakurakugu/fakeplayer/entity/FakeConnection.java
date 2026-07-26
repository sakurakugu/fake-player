package com.sakurakugu.fakeplayer.entity;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** 一个没有远程客户端、会丢弃出站数据的服务端连接。 */
public final class FakeConnection extends Connection {
    public FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        // NeoForge 会在玩家登录期间读取连接通道的属性，内嵌通道用于提供完整的本地连接状态。
        new EmbeddedChannel(this);
    }

    @Override
    public void send(Packet<?> packet) {
        // 假玩家没有对应客户端，所有发往客户端的数据包都直接丢弃。
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }
}
