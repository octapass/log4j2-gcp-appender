package net.octapass.logging.log4j.gcp;

import com.google.cloud.logging.LogEntry;
import org.apache.logging.log4j.core.LogEvent;

import java.util.Map;

/**
 * ContextDataEventEnhancer takes values found in the MDC property map and adds them as labels to the
 * {@link LogEntry}. This {@link LoggingEventEnhancer} is turned on by default. If you wish to filter
 * which MDC values get added as labels to your {@link LogEntry}, implement a {@link LoggingEventEnhancer}
 * and add its classpath to your {@code log4j2.xml}. If any {@link LoggingEventEnhancer} is added
 * this class is no longer registered.
 */
final class ContextDataEventEnhancer implements LoggingEventEnhancer {

    @Override
    public void enhanceLogEntry(LogEntry.Builder builder, LogEvent e) {
        for (Map.Entry<String, String> entry : e.getContextData().toMap().entrySet()) {
            if (null != entry.getKey() && null != entry.getValue()) {
                builder.addLabel(entry.getKey(), entry.getValue());
            }
        }
    }
}
