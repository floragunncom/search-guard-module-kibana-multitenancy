package com.floragunn.searchguard.auditlog.sink;

import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.elasticsearch.common.settings.Settings;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.kafka.test.rule.KafkaEmbedded;

import com.floragunn.searchguard.auditlog.AbstractAuditlogiUnitTest;
import com.floragunn.searchguard.auditlog.helper.MockAuditMessageFactory;
import com.floragunn.searchguard.auditlog.impl.AuditMessage.Category;
import com.floragunn.searchguard.test.helper.file.FileHelper;

@Ignore("not for beta 1")
public class KafkaSinkTest extends AbstractAuditlogiUnitTest {
	
	static boolean running = true;
	
	@ClassRule
	public static KafkaEmbedded embeddedKafka;
	KafkaConsumer<Long, String> consumer;
	
	@BeforeClass
	public static void setUp() throws Exception {
//		embeddedKafka = new KafkaEmbedded(1, true, "compliance");
//		//embeddedKafka.setKafkaPorts(9092);
//		embeddedKafka.before();

//		consumer.subscribe(Arrays.asList("foo", "bar"));
//		while (running) {
//			ConsumerRecords<String, String> records = consumer.poll(100);
//			for (ConsumerRecord<String, String> record : records)
//				System.out.printf("offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
//		}
	}

	@Test
	public void testKafka() throws Exception {
		Settings.Builder settingsBuilder = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/sink/configuration_kafka.yml"));
		// overwrite server, generated automatically on random port
		//settingsBuilder.put("searchguard.audit.config.bootstrap_servers", System.getProperty("spring.embedded.kafka.brokers"));
		KafkaConsumer<Long, String> consumer = getConsumer();
		consumer.subscribe(Arrays.asList("compliance"));
		
		Settings settings = settingsBuilder.put("path.home", ".").build();		
		SinkProvider provider = new SinkProvider(settings, null, null, null);
		AuditLogSink sink = provider.getDefaultSink();
		boolean success = sink.doStore(MockAuditMessageFactory.validAuditMessage(Category.MISSING_PRIVILEGES));
		Assert.assertTrue(success);
		ConsumerRecords<Long, String> records = consumer.poll(1000);
		Assert.assertEquals(1, records.count());
	}

	private KafkaConsumer<Long, String> getConsumer() {
		Properties props = new Properties();
		props.put("bootstrap.servers", "localhost:9092");
		props.put("group.id", "mygroup");
//		props.put("client.id", "elasticsearch_cluster_1");
		props.put("enable.auto.commit", "true");
		props.put("auto.commit.interval.ms", "1000");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.LongDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		consumer = new KafkaConsumer<>(props);
		return consumer;
	}
}
