/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package com.minekube.connect.network.netty;

import com.google.rpc.Code;
import com.google.rpc.Status;
import com.minekube.connect.api.SimpleConnectApi;
import com.minekube.connect.api.logger.ConnectLogger;
import com.minekube.connect.api.player.ConnectPlayer;
import com.minekube.connect.tunnel.TunnelConn;
import com.minekube.connect.tunnel.Tunneler;
import com.minekube.connect.watch.SessionProposal;
import io.grpc.protobuf.StatusProto;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class LocalChannelInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ConnectLogger logger;
    private final SimpleConnectApi api;
    private final Tunneler tunneler;
    private final ConnectPlayer player;
    private final SessionProposal sessionProposal;

    private TunnelConn tunnelConn;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Reject session proposal in case we are still able to and connection was stopped very early.
        rejectProposal(StatusProto.fromThrowable(cause));
        ctx.close();
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
        // Start tunnel from downstream server -> upstream TunnelService
        tunnelConn = tunneler.tunnel(
                sessionProposal.getSession().getTunnelServiceAddr(),
                sessionProposal.getSession().getId(),
                new TunnelHandler(logger, ctx.channel())
        );
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        // Get underlying byte array from buf without copy
        byte[] data = ByteBufUtil.getBytes(buf, buf.readerIndex(), buf.readableBytes(), false);
        // downstream server -> local session server -> TunnelService
        tunnelConn.write(data);
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        api.setPendingRemove(player);
        tunnelConn.close();

        rejectProposal(Status.newBuilder()
                .setCode(Code.UNKNOWN_VALUE)
                .setMessage("local connection closed")
                .build());

        super.channelInactive(ctx);
    }

    private void rejectProposal(Status reason) {
        // Reject session proposal if the tunnel was never opened.
        // Helps to prevent confusion by the watch & tunnel services
        // since a session proposal is automatically accepted a when
        // we opened the tunnel, so we should not send a reject after it.
        if (!tunnelConn.opened()) {
            sessionProposal.reject(reason);
        }
    }
}