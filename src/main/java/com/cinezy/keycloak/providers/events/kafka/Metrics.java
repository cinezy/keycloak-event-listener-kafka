package com.cinezy.keycloak.providers.events.kafka;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Metrics {
  private static final Logger log = Logger.getLogger(Metrics.class.getName());

  private static volatile MeterRegistry registry;
  private static final AtomicInteger inFlight = new AtomicInteger(0);
  private static final Map<String, Timer> sendTimers = new ConcurrentHashMap<>();
  private static final Map<String, DistributionSummary> payloadSizes = new ConcurrentHashMap<>();

  private Metrics() {}

  public static void initIfPossible() {
    if (registry != null) return;
    try {
      InstanceHandle<MeterRegistry> handle = Arc.container().instance(MeterRegistry.class);
      if (handle.isAvailable()) {
        registry = handle.get();
        // static gauges
        Gauge.builder("keycloak_kafka_producer_in_flight", inFlight, AtomicInteger::get)
            .description("Kafka producer in-flight records for Keycloak event listener")
            .register(registry);
      }
    } catch (Throwable ex) {
      // Micrometer not available or metrics disabled: keep no-op
      log.debugf(ex, "Error while initializing metrics registry");
    }
  }

  public static Sample sampleSend(String topic) {
    MeterRegistry r = registry;
    if (r == null) return Sample.NOOP;

    Timer t =
        sendTimers.computeIfAbsent(
            topic,
            tp ->
                Timer.builder("keycloak_kafka_producer_send_latency")
                    .description("Latency of Kafka send() for Keycloak event listener")
                    .tag("topic", tp)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .maximumExpectedValue(Duration.ofSeconds(10))
                    .register(r));

    inFlight.incrementAndGet();
    return new Sample(t, System.nanoTime(), topic);
  }

  public static void recordPayloadSize(String topic, int bytes) {
    MeterRegistry r = registry;
    if (r == null) return;

    payloadSizes
        .computeIfAbsent(
            topic,
            tp ->
                DistributionSummary.builder("keycloak_kafka_producer_payload_bytes")
                    .description("Size of Kafka payloads produced by Keycloak")
                    .baseUnit("bytes")
                    .tag("topic", tp)
                    .publishPercentileHistogram()
                    .register(r))
        .record(bytes);
  }

  public static void markSuccess(String topic) {
    counter("keycloak_kafka_producer_send_total", "outcome", "success", "topic", topic);
  }

  public static void markFailure(String topic, String reasonTag) {
    counter(
        "keycloak_kafka_producer_send_total",
        "outcome",
        "failure",
        "reason",
        reasonTag,
        "topic",
        topic);
  }

  private static void counter(String name, String... tags) {
    MeterRegistry r = registry;
    if (r == null) return;
    r.counter(name, tags).increment();
  }

  public static final class Sample {
    static final Sample NOOP = new Sample(null, 0, null);
    private final Timer timer;
    private final long startNanos;
    private final String topic;

    Sample(Timer timer, long startNanos, String topic) {
      this.timer = timer;
      this.startNanos = startNanos;
      this.topic = topic;
    }

    public void stopSuccess() {
      stop();
      Metrics.markSuccess(topic);
    }

    public void stopFailure(String reason) {
      stop();
      Metrics.markFailure(topic, reason);
    }

    private void stop() {
      if (timer != null) {
        long dur = System.nanoTime() - startNanos;
        timer.record(dur, java.util.concurrent.TimeUnit.NANOSECONDS);
        Metrics.inFlight.decrementAndGet();
      }
    }
  }
}
