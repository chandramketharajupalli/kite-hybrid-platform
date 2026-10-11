package com.kitehybrid.synthetic;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One socket, numeric loopback only; no HTTP client with implicit retry/proxy behavior. */
final class LoopbackPeer {
    private final int port, timeout;
    private final String endpoint;
    LoopbackPeer(int fixturePort, int timeoutMillis) {
        if (fixturePort < 1 || fixturePort > 65535 || timeoutMillis < 1 || timeoutMillis > 3000) throw SyntheticOwner.denied();
        port = fixturePort; timeout = timeoutMillis;
        endpoint = "http://127.0.0.1:" + port + SyntheticOwner.PATH;
    }
    String endpoint() { return endpoint; }
    boolean accepts(String method, String uri) { return "GET".equals(method) && endpoint.equals(uri); }
    SyntheticOwner.Outcome get(String method, String uri) {
        if (!accepts(method, uri) || !SyntheticOwner.environmentSafe()) return SyntheticOwner.Outcome.PROTOTYPE_PRECONDITION_FAILED;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeout);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), port), timeout);
            socket.setSoTimeout(timeout);
            socket.getOutputStream().write(("GET " + SyntheticOwner.PATH + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                    + "\r\nConnection: close\r\nX-Synthetic-Only: true\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream(); var header = new ByteArrayOutputStream();
            int state = 0;
            while (state != 4) {
                int ch = boundedRead(socket, in, deadline); if (ch < 0) return SyntheticOwner.Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN;
                header.write(ch); if (header.size() > 4096) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
                state = switch (state) { case 0 -> ch == 13 ? 1 : 0; case 1 -> ch == 10 ? 2 : 0;
                    case 2 -> ch == 13 ? 3 : 0; case 3 -> ch == 10 ? 4 : 0; default -> 0; };
            }
            String[] lines = header.toString(StandardCharsets.US_ASCII).split("\r\n");
            if (!lines[0].matches("HTTP/1\\.[01] [0-9]{3}( [ -~]*)?")) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
            int status = Integer.parseInt(lines[0].substring(9, 12));
            if (status < 200 || status >= 300) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
            int length = -1;
            for (int i = 1; i < lines.length; i++) {
                String lower = lines[i].toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("transfer-encoding:") || lower.startsWith("content-encoding:")) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
                if (lower.startsWith("content-length:")) {
                    if (length != -1) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
                    length = Integer.parseInt(lines[i].substring(15).trim());
                }
            }
            if (length < 1 || length > 4096) return SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
            byte[] body = new byte[length];
            for (int i = 0; i < length; i++) {
                int ch = boundedRead(socket, in, deadline);
                if (ch < 0) return SyntheticOwner.Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN;
                body[i] = (byte) ch;
            }
            // Deliberately fixture-only schema: strict ordering, no extensions/duplicates, no broker mapper.
            boolean shape = new String(body, StandardCharsets.US_ASCII).matches(
                    "\\{\"synthetic\":true,\"equity\":\\{\"available\":[0-9]{1,9}(\\.[0-9]{1,2})?}}" );
            return shape ? SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED : SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED;
        } catch (Exception ignored) { return SyntheticOwner.Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN; }
    }
    private static int boundedRead(Socket socket, InputStream in, long deadline) throws java.io.IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new java.net.SocketTimeoutException("SYNTHETIC_DEADLINE");
        socket.setSoTimeout((int) Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining)));
        return in.read();
    }
}
