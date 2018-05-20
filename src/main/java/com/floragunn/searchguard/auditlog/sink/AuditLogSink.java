/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use. 
 * For commercial use in a production environment you have to obtain a license 
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.auditlog.sink;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;

public abstract class AuditLogSink {

    protected final Logger log = LogManager.getLogger(this.getClass());
    protected final Settings settings;
    protected final String settingsPrefix;
    private final String name;
    final AuditLogSink fallbackSink;
    
    protected AuditLogSink(String name, Settings settings, String settingsPrefix, AuditLogSink fallbackSink) {
        this.name = name.toLowerCase();
    	this.settings = settings;
        this.settingsPrefix = settingsPrefix;
        this.fallbackSink = fallbackSink;
    }
    
    public boolean isHandlingBackpressure() {
        return false;
    }
    
    public String getName() {
    	return name;
    }
    
    public AuditLogSink getFallbackSink() {
    	return fallbackSink;
    }
    
    public final void store(AuditMessage msg) {
		if (!doStore(msg) && !fallbackSink.doStore(msg)) {
			System.err.println(msg.toPrettyString());
		}
    }
    
    protected abstract boolean doStore(AuditMessage msg);
    
    public void close() throws IOException {
    	// to be implemented by subclasses 
    }
    
    protected String getExpandedIndexName(DateTimeFormatter indexPattern, String index) {
        if(indexPattern == null) {
            return index;
        }
        return indexPattern.print(DateTime.now(DateTimeZone.UTC));
    }
    
    protected Settings getSinkSettings(String prefix) {
    	return settings.getAsSettings(prefix);
    }

    @Override
    public String toString() {    	
    	return ("AudtLogSink: Name: " + name+", type: " + this.getClass().getSimpleName());
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AuditLogSink other = (AuditLogSink) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
    

}
