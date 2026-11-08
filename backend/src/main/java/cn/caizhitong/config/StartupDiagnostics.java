package cn.caizhitong.config;

import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Log completion after all application runners; no endpoints, credentials or user data. */
@Component
public class StartupDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    @EventListener(ApplicationReadyEvent.class)
    public void ready(ApplicationReadyEvent event) {
        var compiler = ManagementFactory.getCompilationMXBean();
        long compilationMs = compiler != null && compiler.isCompilationTimeMonitoringSupported()
            ? compiler.getTotalCompilationTime() : -1;
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        log.info("Application ready: startupMs={}, jvmUptimeMs={}, compilationMs={}, heapUsedMiB={}",
            event.getTimeTaken() == null ? -1 : event.getTimeTaken().toMillis(),
            ManagementFactory.getRuntimeMXBean().getUptime(), compilationMs,
            memory.getUsed() / (1024 * 1024));
    }
}
