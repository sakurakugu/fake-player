package com.sakurakugu.fakeplayer.entity;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** 一个没有远程客户端、会丢弃出站数据的服务端连接。 */
public final class FakeConnection extends Connection {
    private final EmbeddedChannel channel;
    private PacketListener packetListener;
    private boolean disconnectionHandled;

    public FakeConnection() {
        super(PacketFlow.SERVERBOUND);
        // NeoForge 会在玩家登录期间读取连接通道的属性，内嵌通道用于提供完整的本地连接状态。
        channel = new EmbeddedChannel(this);
    }

    @Override
    public void send(Packet<?> packet) {
        // 假玩家没有对应客户端，所有发往客户端的数据包都直接丢弃。
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        completeSend(listener);
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        completeSend(listener);
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
        packetListener = listener;
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {
        packetListener = listener;
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        super.disconnect(details);
        // 假连接不在原版网络连接列表中，需要主动完成退出回调。
        completeDisconnection(details);
    }

    @Override
    public void handleDisconnection() {
        DisconnectionDetails details = getDisconnectionDetails();
        if (details != null) {
            completeDisconnection(details);
        }
    }

    private void completeSend(ChannelFutureListener listener) {
        if (listener != null) {
            // 出站包会被丢弃，但成功回调仍需执行，例如重复登录时的断开操作。
            channel.newSucceededFuture().addListener(listener);
        }
    }

    private void completeDisconnection(DisconnectionDetails details) {
        if (disconnectionHandled) {
            return;
        }
        disconnectionHandled = true;
        if (packetListener != null) {
            packetListener.onDisconnect(details);
        }
    }
}
