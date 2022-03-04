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

package com.minekube.connect.addon.debug;

import com.minekube.connect.api.logger.ConnectLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public final class ChannelOutDebugHandler extends MessageToByteEncoder<ByteBuf> {
    private final String direction;
    private final ConnectLogger logger;

    private final boolean toServer;
    private final StateChangeDetector changeDetector;

    public ChannelOutDebugHandler(
            String implementationType,
            boolean toServer,
            StateChangeDetector changeDetector,
            ConnectLogger logger) {
        this.direction = implementationType + (toServer ? " -> Server" : " -> Player");
        this.logger = logger;
        this.toServer = toServer;
        this.changeDetector = changeDetector;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        try {
            int index = msg.readerIndex();

            if (changeDetector.shouldPrintPacket(msg, toServer)) {
                logger.info(
                        "{} {}:\n{}",
                        direction,
                        changeDetector.getCurrentState(),
                        ByteBufUtil.prettyHexDump(msg)
                );

                // proxy acts as a client when it connects to a server
                changeDetector.checkPacket(msg, toServer);
            }

            // reset index
            msg.readerIndex(index);

            out.writeBytes(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
