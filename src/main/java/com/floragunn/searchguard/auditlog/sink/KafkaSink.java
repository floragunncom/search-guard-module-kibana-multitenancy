package com.floragunn.searchguard.auditlog.sink;

import java.util.Properties;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.elasticsearch.common.settings.Settings;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;

public class KafkaSink extends AuditLogSink {

	boolean valid = true;
	private Producer<Long, String> producer;
	private final String topicName;

	public KafkaSink(final String name, final Settings settings, final Settings sinkSettings, AuditLogSink fallbackSink) {
		super(name, settings, sinkSettings, fallbackSink);

		// mandatory configuration values
		this.topicName = getMandatoryConfigEntry("topic_name");
		String bootstrapServers = getMandatoryConfigEntry("bootstrap_servers");
		String clientId = getMandatoryConfigEntry("client_id");

		if (!valid) {
			log.error("Failed to configure Kafka producer, please check the logfile.");
			return;
		}
		
		// timeouts?
		
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		// props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
		// or node id!
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

		// SSL and / or Kerberos
		this.producer = new KafkaProducer<>(props);

	}

	@Override
	protected boolean doStore(AuditMessage msg) {
		if (!valid) {
			return false;
		}
		ProducerRecord<Long, String> data = new ProducerRecord<Long, String>(topicName, msg.toJson());
		try {
			Future<RecordMetadata> meta = producer.send(data);
			meta.get();
		} catch (Exception e) {
			log.error("Could not store message on Kafka topic {}", this.topicName, e);
			return false;
		}
		return true;
	}

	@Override
	public boolean isHandlingBackpressure() {
		// we use our own thread pool.
		// TODO: make that configurable. But we then loose the ability of properly logging the message into the fallback queue.
		return false;
	}

	private String getMandatoryConfigEntry(String key) {
		String value = sinkSettings.get(key);
		if (value == null || value.length() == 0) {
			log.error("No value for {} provided in configuration, this endpoint will not work.", key);
			this.valid = false;
		}
		return value;
	}

}
