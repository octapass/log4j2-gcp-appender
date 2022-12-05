package net.octapass.logging.log4j.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.util.Log4jThread;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


public final class GoogleCloudLoggingManager extends AbstractManager {

    private static final GoogleCloudLoggingManager2Factory FACTORY = new GoogleCloudLoggingManager2Factory();
    private static final long DEFAULT_TIMEOUT = 7000;

    static {
        LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
    }

    private final String projectId;
    private final Logging logging;
    private final Logger statusLogger;
    private final boolean redirectToStdout;
    private final ArrayBlockingQueue<LogEntry> buffer = null;

    private GoogleCloudLoggingManager(LoggerContext loggerContext,
                                      String name,
                                      String projectId,
                                      Logging logging,
                                      Logger statusLogger,
                                      boolean redirectToStdout) {
        super(loggerContext, name);
        this.projectId = projectId;
        this.logging = logging;
        this.statusLogger = statusLogger;
        this.redirectToStdout = redirectToStdout;
    }

    public static GoogleCloudLoggingManager getManager(final LoggerContext loggerContext,
                                                       final String projectId,
                                                       final String credentialsFileName,
                                                       final Logger statusLogger,
                                                       final boolean redirectToStdout) {


        String managerName = projectId + "@" + credentialsFileName;
        return getManager(managerName, FACTORY, new FactoryData(loggerContext, projectId, credentialsFileName,
                statusLogger, redirectToStdout));
    }

    public boolean releaseSub(final long timeout, final TimeUnit timeUnit) {
        if (timeout > 0) {
            closeProducer(timeout, timeUnit);
        } else {
            closeProducer(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    private void closeProducer(final long timeout, final TimeUnit timeUnit) {
        if (logging != null) {
            final Thread closeThread = new Log4jThread(() -> {
                try {
                    logging.close();
                } catch (Exception e) {
                    statusLogger.warn(e);
                }
            }, "GoogleCloudLoggingManager-CloseThread");
            closeThread.setDaemon(true); // avoid blocking JVM shutdown
            closeThread.start();
            try {
                closeThread.join(timeUnit.toMillis(timeout));
            } catch (final InterruptedException ignore) {
                Thread.currentThread().interrupt();
                // ignore
            }
        }
    }

    public void writeLogEntry(LogEntry logEntry) {
        if (buffer != null) {
            while (!buffer.add(logEntry)) {
                tryFlush(logging, buffer, redirectToStdout);
            }
        } else {
            if (redirectToStdout) {
                System.out.println(logEntry.toStructuredJsonString());
            } else {
                logging.write(Collections.singleton(logEntry));
            }
        }
    }

    public void flush() {
        this.logging.flush();
    }

    private static void tryFlush(Logging logging, ArrayBlockingQueue<LogEntry> buffer, boolean redirectToStdout) {
        if (buffer == null) {
            return;
        }

        if (redirectToStdout) {
            for (LogEntry entry : buffer) {
                System.out.println(entry.toStructuredJsonString());
            }
            buffer.clear();
        } else {
            logging.write(buffer);
            buffer.clear();
            logging.flush();
        }
    }

    private record FactoryData(LoggerContext loggerContext,
                               String projectId,
                               String credentialsFile,
                               Logger statusLogger,
                               boolean redirectToStdout) {
    }

    private static class GoogleCloudLoggingManager2Factory implements
            ManagerFactory<GoogleCloudLoggingManager, FactoryData> {

        @Override
        public GoogleCloudLoggingManager createManager(String name, FactoryData data) {
            String credFile = data.credentialsFile;

            try {
                LoggingOptions loggingOptions;
                if (credFile == null) {
                    loggingOptions = LoggingOptions.getDefaultInstance();
                } else {
                    loggingOptions = LoggingOptions.newBuilder().setAutoPopulateMetadata(true).setCredentials(
                            GoogleCredentials.fromStream(new FileInputStream(credFile))).build();
                }

                return new GoogleCloudLoggingManager(data.loggerContext, name, data.projectId,
                        loggingOptions.getService(), data.statusLogger, data.redirectToStdout);
            } catch (Exception e) {
                data.statusLogger.error(e);
            }
            return null;
        }
    }

}
