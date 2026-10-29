package cn.dreamingfish.launcher.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Fixed official source; the package-private constructor is for loopback HTTP tests. */
public final class HttpManifestTransport implements ManifestTransport {
    public static final URI OFFICIAL_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI uri;
    private final Duration timeout;
    public HttpManifestTransport() { this(OFFICIAL_URI, REQUEST_TIMEOUT); }
    HttpManifestTransport(URI uri, Duration timeout) { this.uri = uri; this.timeout = timeout; }

    @Override public byte[] fetch() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json")
                .header("User-Agent", "SanQiuYe-Launcher/0.2 M2A").GET().build();
        var pending = CLIENT.sendAsync(request, info -> new LimitedBody());
        try {
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new IOException("官方目录请求返回 HTTP " + response.statusCode());
            return response.body();
        } catch (TimeoutException error) {
            pending.cancel(true);
            throw new HttpTimeoutException("官方目录请求超过时间限制");
        } catch (InterruptedException error) {
            pending.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException io) throw io;
            throw new IOException("无法读取官方版本目录", error.getCause());
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > VersionManifestParser.MAX_BYTES) {
                    subscription.cancel(); body.completeExceptionally(new IOException("官方版本目录超过 4 MiB")); return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
