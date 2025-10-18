package com.cinezy.keycloak.providers.events.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class KafkaProducerHolder {
  private static volatile Producer<byte[], byte[]> producer;

  private KafkaProducerHolder() {}

  public static synchronized void initIfNeeded(
      String bootstrapServers,
      String acks,
      Integer lingerMs,
      Integer batchSize,
      Integer retries,
      String securityProtocol,
      String saslMechanism,
      String saslJaasConfig) {
    if (producer != null) return;

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.ACKS_CONFIG, acks);
    props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
    props.put(ProducerConfig.RETRIES_CONFIG, retries);
    props.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    props.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1); // order

    if (securityProtocol != null) props.put("security.protocol", securityProtocol);
    if (saslMechanism != null) props.put("sasl.mechanism", saslMechanism);
    if (saslJaasConfig != null) props.put("sasl.jaas.config", saslJaasConfig);

    producer = new KafkaProducer<>(props);
  }

  public static void sendAsync(String topic, String key, String json) {
    var rec = new ProducerRecord<>(topic, bytes(key), bytes(json));
    producer.send(
        rec,
        (meta, ex) -> {
          if (ex != null) {
            System.err.println("[kafkalogger] async send error: " + ex.getMessage());
          }
        });
  }

  public static void sendSync(String topic, String key, String json) throws Exception {
    var rec = new ProducerRecord<>(topic, bytes(key), bytes(json));
    producer.send(rec).get(10, TimeUnit.SECONDS);
  }

  private static byte[] bytes(String s) {
    return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
  }
}
