package net.octapass.logging.log4j.gcp;

import com.google.cloud.logging.LogEntry;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;

/** Adds support for grouping logs by incoming http request.*/
public class TraceLoggingEventEnhancer implements LoggingEventEnhancer {

    // A key used by Cloud Logging for trace Id
    private static final String TRACE_ID = "logging.googleapis.trace";

    /**
     * Set the Trace ID associated with any logging done by the current thread.
     *
     * @param id The traceID, in the form projects/[PROJECT_ID]/traces/[TRACE_ID]
     */
    public static void setCurrentTraceId(String id) {
        ThreadContext.put(TRACE_ID, id);
    }

    /** Clearing a trace Id from the MDC.*/
    public static void clearTraceId() {
        ThreadContext.remove(TRACE_ID);
    }

    /**
     * Get the Trace ID associated with any logging done by the current thread.
     *
     * @return id The traceID
     */
    public static String getCurrentTraceId() {
        return ThreadContext.get(TRACE_ID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enhanceLogEntry(LogEntry.Builder builder, LogEvent e) {
        Object value = e.getContextData().getValue(TRACE_ID);
        String traceId = value != null ? value.toString() : null;
        if (traceId != null) {
            builder.setTrace(traceId);
        }
    }
}
