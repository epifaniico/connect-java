/*
 * Copyright (c) 2021-2022 Minekube. https://minekube.com
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
 * @author Minekube
 * @link https://github.com/minekube/connect-java
 */

package com.minekube.connect.network.netty;

import com.minekube.connect.api.logger.ConnectLogger;
import com.minekube.connect.tunnel.TunnelConn.Handler;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class TunnelHandler implements Handler {
    private final ConnectLogger logger;
    private final Channel downstreamServerConn; // local server connection

    @Override
    public void onReceive(byte[] data) {
        // TunnelService -> local session server -> downstream server
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        downstreamServerConn.writeAndFlush(buf);
    }

    @Override
    public void onError(Throwable t) {
        // error connecting to tunnel service
        Status status = Status.fromThrowable(t);
        if (status.getCode() == Code.CANCELLED) {
            return;
        }
        logger.error("Connection error with TunnelService: " +
                        t + (
                        t.getCause() == null ? ""
                                : " (cause: " + t.getCause().toString() + ")"
                )
        );
    }

    @Override
    public void onClose() {
        // disconnect from downstream server
        downstreamServerConn.close();
    }
}
