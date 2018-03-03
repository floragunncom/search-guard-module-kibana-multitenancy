package com.floragunn.searchguard.auditlog.sink;

import java.util.ArrayList;
import java.util.List;

import com.floragunn.searchguard.auditlog.impl.AuditMessage;

public class LoggingSink extends AuditLogSink {

	public List<AuditMessage> messages = new ArrayList<AuditMessage>(100);
    public StringBuffer sb = new StringBuffer();
    
    public LoggingSink(String name, AuditLogSink fallbackSink) {
        super(name, null, null, fallbackSink);
    }

    
    public boolean doStore(AuditMessage msg) {
        sb.append(msg.toPrettyString()+System.lineSeparator());
        messages.add(msg);
        return true;
    }
    
    public synchronized void clear() {
        sb.setLength(0);
        messages.clear();
    }

    @Override
    public boolean isHandlingBackpressure() {
        return true;
    }
    
}
