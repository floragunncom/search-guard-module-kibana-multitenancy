package com.floragunn.searchguard.auditlog.sink;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

import com.floragunn.searchguard.test.helper.file.FileHelper;

import org.junit.Assert;

public class SinkProviderTest {

	@Test
	public void testConfiguration() throws Exception {
		
		Settings settings = Settings.builder().loadFromPath(FileHelper.getAbsoluteFilePathFromClassPath("auditlog/endpoints/sink/configuration_all_variants.yml")).build();
		SinkProvider provider = new SinkProvider(settings, null, null, null);
		
		AuditLogSink sink = provider.getSink("DefaULT");
		Assert.assertEquals(sink.getClass(), DebugSink.class);

		sink = provider.getSink("endpoint1");
		Assert.assertEquals(sink.getClass(), InternalESSink.class);

		sink = provider.getSink("endpoint2");
		Assert.assertEquals(sink.getClass(), ExternalESSink.class);
		// todo: sink does not work

		sink = provider.getSink("endpoinT3");
		Assert.assertEquals(sink.getClass(), DebugSink.class);
		
		// no valid type
		sink = provider.getSink("endpoint4");
		Assert.assertEquals(null, sink);

		sink = provider.getSink("endpoint2");
		Assert.assertEquals(sink.getClass(), ExternalESSink.class);
		// todo: sink does not work, no valid config

		// no valid type
		sink = provider.getSink("endpoint6");
		Assert.assertEquals(null, sink);

		// no valid type
		sink = provider.getSink("endpoint7");
		Assert.assertEquals(null, sink);

		sink = provider.getSink("endpoint8");
		Assert.assertEquals(sink.getClass(), DebugSink.class);

		// wrong type in config
		sink = provider.getSink("endpoint9");
		Assert.assertEquals(sink.getClass(), ExternalESSink.class);

	}
}
