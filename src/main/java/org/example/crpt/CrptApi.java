package org.example.crpt;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final RateLimiter rateLimiter;
    private final CrptHttpClient httpClient;

    private CrptApi(RateLimiter rateLimiter, CrptHttpClient httpClient) {
        this.rateLimiter = rateLimiter;
        this.httpClient = httpClient;
    }

    public CrptApi(TimeUnit timeUnit, int requestsLimit) {
        if (requestsLimit <= 0) {
            throw new IllegalArgumentException("requestsLimit must be greater than 0");
        }
        this.rateLimiter = new SlidingWindowRateLimiter(requestsLimit, timeUnit.toMillis(1));
        this.httpClient = new JDK17CrptHttpClient(URI.create("https://ismp.crpt.ru/api/v3/lk"));
    }

    public CreateDocumentResponse createDocument(Document document, String signature) {
        rateLimiter.consume();
        return httpClient.post("/documents/create", new CreateDocumentRequest(document, signature), CreateDocumentResponse.class);
    }
}


record CreateDocumentRequest(Document document, String signature) {}

interface CrptHttpClient {

    <T> T post(String url, Object body, Class<T> responseClass);
}


class JDK17CrptHttpClient implements CrptHttpClient {

    private final URI baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    private HttpClient client = HttpClient.newHttpClient();

    public JDK17CrptHttpClient(HttpClient client, URI baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public JDK17CrptHttpClient(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public <T> T post(String path, Object body, Class<T> response) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(path))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return mapper.readValue(res.body(), response);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

interface RateLimiter {
    void consume();
}


class SlidingWindowRateLimiter implements RateLimiter {

    private final SortedMap<Long, Integer> window;

    private Clock clock;

    private final int windowSize;

    private final long timeLimit;

    private final ScheduledExecutorService executorService;

    /**
     * @param windowSize the maximum number of requests allowed in the window
     * @param timeLimit  the time limit in milliseconds
     */
    SlidingWindowRateLimiter(int windowSize, long timeLimit) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be greater than 0");
        }

        if (timeLimit <= 0) {
            throw new IllegalArgumentException("Time limit must be greater than 0");
        }

        this.window = new TreeMap<>();
        this.windowSize = windowSize;
        this.timeLimit = timeLimit;
        this.clock = Clock.systemDefaultZone();
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.executorService.scheduleAtFixedRate(this::cleanup, 100, 100, TimeUnit.MILLISECONDS);
    }

    SlidingWindowRateLimiter(int windowSize, int timeLimit, Clock clock) {
        this(windowSize, timeLimit);
        this.clock = clock;
    }

    /**
     * Consumes a request.
     */
    @Override
    public synchronized void consume() {
        while (getWindowSize(clock.millis()) >= windowSize) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        window.compute(clock.millis(), (key, value) -> value == null ? 1 : value + 1);
    }

    /**
     * Removes all entries that are older than the time limit.
     */
    private synchronized void cleanup() {
        window.headMap(clock.millis() - timeLimit).clear();
        notifyAll();
    }

    private int getWindowSize(long currentTime) {
        int size = 0;
        for (var b : window.tailMap(currentTime - timeLimit).values()) {
            size += b;
        }
        return size;
    }
}
