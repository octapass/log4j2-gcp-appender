package net.octapass.logging.log4j.gcp;

import com.google.cloud.logging.LogEntry;
import org.apache.logging.log4j.core.LogEvent;

/**
 * An enhancer for {@linkplain LogEvent} log entries. Used to add custom labels to the {@link
 * LogEntry.Builder}.
 */
public interface LoggingEventEnhancer {
    void enhanceLogEntry(LogEntry.Builder builder, LogEvent e);
}
