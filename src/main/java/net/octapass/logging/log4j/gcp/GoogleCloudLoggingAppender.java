package net.octapass.logging.log4j.gcp;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.*;
import com.google.common.collect.ImmutableList;
import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.impl.ExtendedStackTraceElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Loader;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Logging Appender for Apache Log4J2.
 *
 * @author Andrei Petrenko <andrei.petrenko@octapass.com>
 * <p>
 * This {@link org.apache.logging.log4j.core.Appender} forwards log messages to
 * <a href="https://cloud.google.com/logging/docs/">Google Cloud Logging</a>.
 * If not specified, it uses the default project id (set by glcoud config set) and the application default credentials.
 */
@Plugin(name = "GoogleCloudLogging", category = Node.CATEGORY, elementType = "appender", printObject = true)
public class GoogleCloudLoggingAppender extends AbstractAppender {

    static {
        LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
    }

    private static final String TYPE =
            "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";
    private static final String LEVEL_NAME_KEY = "levelName";
    private static final String LEVEL_VALUE_KEY = "levelValue";
    private static final String LOGGER_NAME_KEY = "loggerName";
    private static final List<LoggingEventEnhancer> DEFAULT_LOGGING_EVENT_ENHANCERS =
            ImmutableList.of(new ContextDataEventEnhancer());

    private final GoogleCloudLoggingManager manager;
    private final String gcpLogName;
    private final MonitoredResource monitoredResource;
    private final List<LoggingEnhancer> loggingEnhancers;
    private final List<LoggingEventEnhancer> loggingEventEnhancers;
    private final Set<String> enhancerClassNames = new HashSet<>();
    private final Set<String> loggingEventEnhancerClassNames = new HashSet<>();


    protected GoogleCloudLoggingAppender(GoogleCloudLoggingManager manager,
                                         String name,
                                         String gcpLogName,
                                         Filter filter,
                                         Layout<? extends Serializable> layout,
                                         boolean ignoreExceptions,
                                         Property[] properties,
                                         MonitoredResource monitoredResource) {
        super(name, filter, layout, ignoreExceptions, properties);
        this.manager = manager;
        this.monitoredResource = monitoredResource;
        this.gcpLogName = gcpLogName;

        loggingEnhancers = new ArrayList<>();
        List<LoggingEnhancer> resourceEnhancers = MonitoredResourceUtil.getResourceEnhancers();
        loggingEnhancers.addAll(resourceEnhancers);
        loggingEnhancers.addAll(getLoggingEnhancers());
        loggingEventEnhancers = new ArrayList<>();
        loggingEventEnhancers.addAll(getLoggingEventEnhancers());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void append(LogEvent event) {
        manager.writeLogEntry(logEntryFor(event));
        if (event.isEndOfBatch()) {
            manager.flush();
        }
    }

    private List<LoggingEnhancer> getLoggingEnhancers() {
        return getEnhancers(enhancerClassNames, LoggingEnhancer.class);
    }

    private List<LoggingEventEnhancer> getLoggingEventEnhancers() {
        if (loggingEventEnhancerClassNames.isEmpty()) {
            return DEFAULT_LOGGING_EVENT_ENHANCERS;
        } else {
            return getEnhancers(loggingEventEnhancerClassNames, LoggingEventEnhancer.class);
        }
    }

    private <T> List<T> getEnhancers(Set<String> classNames, Class<T> classOfT) {
        List<T> enhancers = new ArrayList<>();
        if (classNames != null) {
            for (String className : classNames) {
                if (className != null) {
                    try {
                        T enhancer =
                                Loader.loadClass(className.trim())
                                        .asSubclass(classOfT)
                                        .getDeclaredConstructor()
                                        .newInstance();
                        enhancers.add(enhancer);
                    } catch (Exception ex) {
                        // invalid className: ignore
                    }
                }
            }
        }
        return enhancers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        super.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit, false);
        stopped &= manager.stop(timeout, timeUnit);
        setStopped();
        return stopped;
    }

    private static SourceLocation getSourceLocation(LogEvent event) {
        @Nullable StackTraceElement source = event.getSource();
        if (source == null) {
            return null;
        }
        SourceLocation.Builder builder = SourceLocation.newBuilder();

        builder.setFile(source.getFileName());
        builder.setFunction(source.getMethodName());
        builder.setLine((long) source.getLineNumber());

        return builder.build();
    }

    private static Instant getTimestamp(LogEvent event) {
        org.apache.logging.log4j.core.time.Instant logInstant = event.getInstant();
        return Instant.ofEpochSecond(logInstant.getEpochSecond(), logInstant.getNanoOfSecond());
    }

    private static Severity severityFor(Level level) {
        switch (level.getStandardLevel()) {
            case FATAL -> {
                return Severity.ALERT;
            }
            case ERROR -> {
                return Severity.ERROR;
            }
            case WARN -> {
                return Severity.WARNING;
            }
            case INFO -> {
                return Severity.INFO;
            }
            case DEBUG, TRACE, ALL -> {
                return Severity.DEBUG;
            }
            default -> {
                return Severity.DEFAULT;
            }
        }
    }


    private LogEntry logEntryFor(LogEvent event) {
        StringBuilder payload = new StringBuilder().append(event.getMessage().getFormattedMessage()).append('\n');
        writeStack(event.getThrownProxy(), "", payload);

        Level level = event.getLevel();
        Severity severity = severityFor(level);

        Map<String, Object> jsonContent = new HashMap<>();
        jsonContent.put("message", payload.toString().trim());
        if (severity == Severity.ERROR) {
            jsonContent.put("@type", TYPE);
        }

        LogEntry.Builder builder =
                LogEntry.newBuilder(Payload.JsonPayload.of(jsonContent))
                        .setLogName(gcpLogName)
                        .setTimestamp(getTimestamp(event))
                        .setSeverity(severity)
                        .setResource(monitoredResource);


        builder
                .addLabel(LEVEL_NAME_KEY, level.toString())
                .addLabel(LEVEL_VALUE_KEY, String.valueOf(level.intLevel()))
                .addLabel(LOGGER_NAME_KEY, event.getLoggerName());

        if (event.isIncludeLocation()) {
            builder.setSourceLocation(getSourceLocation(event));
        }

        if (loggingEnhancers != null) {
            for (LoggingEnhancer enhancer : loggingEnhancers) {
                enhancer.enhanceLogEntry(builder);
            }
        }

        if (loggingEventEnhancers != null) {
            for (LoggingEventEnhancer enhancer : loggingEventEnhancers) {
                enhancer.enhanceLogEntry(builder, event);
            }
        }

        return builder.build();
    }

    static void writeStack(ThrowableProxy throwProxy, String prefix, StringBuilder payload) {
        if (throwProxy == null) {
            return;
        }
        payload
                .append(prefix)
                .append(throwProxy.getName())
                .append(": ")
                .append(throwProxy.getMessage())
                .append('\n');
        ExtendedStackTraceElement[] trace = throwProxy.getExtendedStackTrace();
        if (trace == null) {
            trace = new ExtendedStackTraceElement[0];
        }

        int commonFrames = throwProxy.getCommonElementCount();
        int printFrames = trace.length - commonFrames;
        for (int i = 0; i < printFrames; i++) {
            payload.append("    ").append(trace[i]).append('\n');
        }
        if (commonFrames != 0) {
            payload.append("    ... ").append(commonFrames).append(" common frames elided\n");
        }

        writeStack(throwProxy.getCauseProxy(), "caused by: ", payload);
    }

    @PluginBuilderFactory
    public static <B extends GoogleCloudLoggingAppender.Builder<B>> B newBuilder() {
        return new GoogleCloudLoggingAppender.Builder<B>().asBuilder();
    }

    public static class Builder<B extends AbstractAppender.Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<GoogleCloudLoggingAppender> {
        private static final int DEFAULT_BUFFER_SIZE = 50;
        @PluginElement("Layout")
        private Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();

        @PluginElement("Filter")
        private Filter filter;

        @PluginElement("Resource")
        private GCPResourceConfig resource;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        @Required
        @PluginBuilderAttribute
        private String gcpLogName;

        @PluginBuilderAttribute
        private String projectId;

        @PluginBuilderAttribute
        private String credentialsFile;

        @PluginBuilderAttribute
        private boolean autoPopulateMetadata = true;

        @PluginBuilderAttribute
        private boolean redirectToStdout = false;

        @PluginBuilderAttribute
        private Boolean buffered = null;

        @PluginBuilderAttribute
        private int bufferSize = DEFAULT_BUFFER_SIZE;

        /**
         * {@inheritDoc}
         */
        @Override
        public GoogleCloudLoggingAppender build() {
            try {
                LoggerContext context = getConfiguration().getLoggerContext();
                boolean isBuffered = (context instanceof AsyncLoggerContext) && (buffered == null || buffered);

                String localProjectId = projectId;

                String resourceType = null;
                if (resource != null) {
                    resourceType = resource.resourceType();
                }
                Map<String, String> resourceLabels = new HashMap<>();

                try {
                    MonitoredResource monResource = MonitoredResourceUtil.getResource(localProjectId, resourceType);

                    if (localProjectId == null || localProjectId.isBlank()) {
                        localProjectId = monResource.getLabels().get("project_id");
                    }

                    if (resourceType == null || resourceType.isBlank()) {
                        resourceType = monResource.getType();
                    }

                    resourceLabels.putAll(monResource.getLabels());
                } catch (NullPointerException e) {
                    getStatusLogger().info("Could not determine project ID automatically");
                }

                if (resource != null) {
                    for (int i = 0; i < resource.labels().length; i++) {
                        ResourceLabel label = resource.labels()[i];
                        resourceLabels.put(label.name(), label.value());
                    }
                }

                MonitoredResource.Builder resourceBuilder = MonitoredResource.newBuilder(resourceType);
                resourceBuilder.setLabels(resourceLabels);

                GoogleCloudLoggingManager manager = GoogleCloudLoggingManager.getManager(context, localProjectId,
                        credentialsFile, getStatusLogger(), redirectToStdout);

                return new GoogleCloudLoggingAppender(manager, getName(), gcpLogName, filter, layout, ignoreExceptions,
                        null, resourceBuilder.build());
            } catch (final Throwable e) {
                getStatusLogger().error("Error creating GoogleCloudLoggingAppender [{}]", getName(), e);
                return null;
            }
        }
    }
}
