package com.cinezy.keycloak.providers.events.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SslConfigs;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class KafkaProducerHolder {
  private static final Logger log = Logger.getLogger(KafkaProducerHolder.class.getName());
  private static volatile Producer<byte[], byte[]> producer;

  private KafkaProducerHolder() {}

  public static synchronized void initIfNeeded(Config cfg) {
    if (producer != null) return;
    Objects.requireNonNull(cfg, "Kafka config must not be null");

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cfg.bootstrapServers);
    props.put(ProducerConfig.CLIENT_ID_CONFIG, cfg.clientId);
    props.put(ProducerConfig.ACKS_CONFIG, cfg.acks);
    props.put(ProducerConfig.RETRIES_CONFIG, cfg.retries);
    props.put(ProducerConfig.LINGER_MS_CONFIG, cfg.lingerMs);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, cfg.batchSize);
    if (cfg.compressionType != null) {
      props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, cfg.compressionType);
    }
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, cfg.deliveryTimeoutMs);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, cfg.requestTimeoutMs);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, cfg.enableIdempotence);
    props.put(
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, cfg.maxInFlightRequestsPerConnection);

    if (cfg.securityProtocol != null) props.put("security.protocol", cfg.securityProtocol);
    if (cfg.saslMechanism != null) props.put("sasl.mechanism", cfg.saslMechanism);
    if (cfg.saslJaasConfig != null) props.put("sasl.jaas.config", cfg.saslJaasConfig);

    if (cfg.sslTruststoreLocation != null)
      props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, cfg.sslTruststoreLocation);
    if (cfg.sslTruststorePassword != null)
      props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, cfg.sslTruststorePassword);
    if (cfg.sslKeystoreLocation != null)
      props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, cfg.sslKeystoreLocation);
    if (cfg.sslKeystorePassword != null)
      props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, cfg.sslKeystorePassword);
    if (cfg.sslKeyPassword != null)
      props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, cfg.sslKeyPassword);

    if (cfg.extraProps != null && !cfg.extraProps.isEmpty()) {
      props.putAll(cfg.extraProps);
    }

    props.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    props.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");

    if (Boolean.FALSE.equals(cfg.enableIdempotence)) {
      props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    }

    producer = new KafkaProducer<>(props);
  }

  public static void sendAsync(String topic, String key, String json) {
    log.debugf("Sending event to Kafka: topic=%s, key=%s, json=%s", topic, key, json);
    var rec = new ProducerRecord<>(topic, bytes(key), bytes(json));
    producer.send(
        rec,
        (meta, ex) -> {
          if (ex != null) {
            log.errorf(ex, "Failed to publish event to Kafka");
          }
        });
  }

  public static void sendSync(String topic, String key, String json) throws Exception {
    log.debugf("Sending event to Kafka: topic=%s, key=%s, json=%s", topic, key, json);
    var rec = new ProducerRecord<>(topic, bytes(key), bytes(json));
    producer.send(rec).get(10, TimeUnit.SECONDS);
  }

  private static byte[] bytes(String s) {
    return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
  }

  public static final class Config {
    // Connection
    public String bootstrapServers;
    public String clientId = "keycloak";

    // Producer knobs
    public String acks = "all";
    public Integer retries = 3;
    public Integer lingerMs = 5;
    public Integer batchSize = 16384;
    public String compressionType; // gzip/snappy/lz4/zstd
    public Integer deliveryTimeoutMs = 120_000;
    public Integer requestTimeoutMs = 30_000;
    public Boolean enableIdempotence = true;
    public Integer maxInFlightRequestsPerConnection = 5;

    // Security / SASL / SSL
    public String securityProtocol; // PLAINTEXT / SSL / SASL_SSL
    public String saslMechanism; // PLAIN / SCRAM-SHA-256 / SCRAM-SHA-512
    public String saslJaasConfig; // JAAS string
    public String sslTruststoreLocation;
    public String sslTruststorePassword;
    public String sslKeystoreLocation;
    public String sslKeystorePassword;
    public String sslKeyPassword;

    // Extra passthrough properties (props.*)
    public Properties extraProps = new Properties();
  }
}
