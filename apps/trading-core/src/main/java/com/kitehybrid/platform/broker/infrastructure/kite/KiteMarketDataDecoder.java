package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.domain.StreamMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parses the official Kite Connect v3 big-endian protocol without I/O.
 * A malformed packet rejects the entire message; no partially decoded batch escapes.
 * The limits cover one full-mode update for the broker's 3000 subscription limit.
 */
public final class KiteMarketDataDecoder {
    public static final int MAX_PACKETS = 3000;
    public static final int MAX_FRAME_BYTES = 2 + MAX_PACKETS * (2 + 184);

    public List<KiteWireTick> decode(byte[] frame) {
        if (frame == null || frame.length == 0) throw malformed("Empty binary message");
        if (frame.length == 1) return List.of(); // The protocol's one-byte heartbeat.
        if (frame.length > MAX_FRAME_BYTES) throw malformed("Binary message exceeds limit");
        ByteBuffer input = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        int count = Short.toUnsignedInt(input.getShort());
        if (count == 0 || count > MAX_PACKETS || count > input.remaining() / 10)
            throw malformed("Invalid packet count");
        List<KiteWireTick> ticks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (input.remaining() < 2) throw malformed("Missing packet length");
            int length = Short.toUnsignedInt(input.getShort());
            if (length != 8 && length != 28 && length != 32 && length != 44 && length != 184)
                throw malformed("Unsupported packet length");
            if (input.remaining() < length) throw malformed("Truncated packet");
            ByteBuffer packet = input.slice(input.position(), length).order(ByteOrder.BIG_ENDIAN);
            ticks.add(decodePacket(packet, length));
            input.position(input.position() + length);
        }
        if (input.hasRemaining()) throw malformed("Trailing binary data");
        return List.copyOf(ticks);
    }

    private KiteWireTick decodePacket(ByteBuffer packet, int length) {
        long token = unsigned(packet, 0);
        if (token == 0) throw malformed("Missing instrument token");
        long price = unsigned(packet, 4);
        if (length == 8) return new KiteWireTick(token, StreamMode.LTP, price,
                OptionalLong.empty(), Optional.empty(), Optional.empty());

        boolean index = (token & 0xff) == 9;
        if ((length == 28 || length == 32) != index)
            throw malformed("Packet shape does not match instrument segment");
        if (index) {
            var ohlc = new KiteWireTick.Ohlc(unsigned(packet, 16), unsigned(packet, 8),
                    unsigned(packet, 12), unsigned(packet, 20));
            var quote = new KiteWireTick.Quote(OptionalLong.empty(), OptionalLong.empty(), ohlc);
            return new KiteWireTick(token, length == 28 ? StreamMode.QUOTE : StreamMode.FULL,
                    price, length == 32 ? timestamp(packet, 28) : OptionalLong.empty(),
                    Optional.of(quote), Optional.empty());
        }

        var ohlc = new KiteWireTick.Ohlc(unsigned(packet, 28), unsigned(packet, 32),
                unsigned(packet, 36), unsigned(packet, 40));
        var quote = new KiteWireTick.Quote(OptionalLong.of(unsigned(packet, 8)),
                OptionalLong.of(unsigned(packet, 16)), ohlc);
        return new KiteWireTick(token, length == 44 ? StreamMode.QUOTE : StreamMode.FULL,
                price, length == 184 ? timestamp(packet, 60) : OptionalLong.empty(),
                Optional.of(quote), length == 184 ? Optional.of(depth(packet)) : Optional.empty());
    }

    private KiteWireTick.Depth depth(ByteBuffer packet) {
        List<KiteWireTick.Level> bids = new ArrayList<>(5);
        List<KiteWireTick.Level> asks = new ArrayList<>(5);
        for (int level = 0; level < 10; level++) {
            int offset = 64 + level * 12;
            var entry = new KiteWireTick.Level(unsigned(packet, offset + 4), unsigned(packet, offset),
                    Short.toUnsignedInt(packet.getShort(offset + 8)));
            (level < 5 ? bids : asks).add(entry);
            // The final two bytes are protocol padding and deliberately ignored.
        }
        return new KiteWireTick.Depth(bids, asks);
    }

    private static long unsigned(ByteBuffer packet, int offset) {
        return Integer.toUnsignedLong(packet.getInt(offset));
    }

    private static OptionalLong timestamp(ByteBuffer packet, int offset) {
        long seconds = unsigned(packet, offset);
        // An unset broker timestamp must not masquerade as a known 1970 exchange time.
        return seconds == 0 ? OptionalLong.empty() : OptionalLong.of(seconds);
    }

    private static DecodeException malformed(String reason) { return new DecodeException(reason); }

    /** Fixed safe messages only; raw binary payloads are never included. */
    public static final class DecodeException extends IllegalArgumentException {
        private DecodeException(String reason) { super(reason); }
    }
}
