package com.nginx.test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus-compatible metrics endpoint for Tomcat
 * Exposes JVM and application metrics in Prometheus text format
 */
public class MetricsServlet extends HttpServlet {

    // Application metrics counters
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final AtomicLong errorCounter = new AtomicLong(0);
    private static final AtomicLong requestDurationTotal = new AtomicLong(0);

    public static void incrementRequests() {
        requestCounter.incrementAndGet();
    }

    public static void incrementErrors() {
        errorCounter.incrementAndGet();
    }

    public static void addRequestDuration(long durationMs) {
        requestDurationTotal.addAndGet(durationMs);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain; version=0.0.4; charset=utf-8");
        PrintWriter out = response.getWriter();

        StringBuilder metrics = new StringBuilder();

        // Service info
        metrics.append("# HELP was_info Service information\n");
        metrics.append("# TYPE was_info gauge\n");
        metrics.append("was_info{service=\"was-1-tomcat\",type=\"legacy\",runtime=\"tomcat9\"} 1\n\n");

        // JVM Memory metrics
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        metrics.append("# HELP jvm_memory_bytes_used JVM memory used\n");
        metrics.append("# TYPE jvm_memory_bytes_used gauge\n");
        metrics.append(String.format("jvm_memory_bytes_used{area=\"heap\"} %d\n",
                memoryMXBean.getHeapMemoryUsage().getUsed()));
        metrics.append(String.format("jvm_memory_bytes_used{area=\"nonheap\"} %d\n\n",
                memoryMXBean.getNonHeapMemoryUsage().getUsed()));

        metrics.append("# HELP jvm_memory_bytes_max JVM memory max\n");
        metrics.append("# TYPE jvm_memory_bytes_max gauge\n");
        metrics.append(String.format("jvm_memory_bytes_max{area=\"heap\"} %d\n",
                memoryMXBean.getHeapMemoryUsage().getMax()));
        metrics.append(String.format("jvm_memory_bytes_max{area=\"nonheap\"} %d\n\n",
                memoryMXBean.getNonHeapMemoryUsage().getMax()));

        // Thread metrics
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        metrics.append("# HELP jvm_threads_current Current thread count\n");
        metrics.append("# TYPE jvm_threads_current gauge\n");
        metrics.append(String.format("jvm_threads_current %d\n\n", threadMXBean.getThreadCount()));

        metrics.append("# HELP jvm_threads_daemon Daemon thread count\n");
        metrics.append("# TYPE jvm_threads_daemon gauge\n");
        metrics.append(String.format("jvm_threads_daemon %d\n\n", threadMXBean.getDaemonThreadCount()));

        metrics.append("# HELP jvm_threads_peak Peak thread count\n");
        metrics.append("# TYPE jvm_threads_peak gauge\n");
        metrics.append(String.format("jvm_threads_peak %d\n\n", threadMXBean.getPeakThreadCount()));

        // GC metrics
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        metrics.append("# HELP jvm_gc_collection_seconds_total Total GC collection time\n");
        metrics.append("# TYPE jvm_gc_collection_seconds_total counter\n");
        for (GarbageCollectorMXBean gc : gcBeans) {
            String gcName = gc.getName().replace(" ", "_").toLowerCase();
            metrics.append(String.format("jvm_gc_collection_seconds_total{gc=\"%s\"} %.3f\n",
                    gcName, gc.getCollectionTime() / 1000.0));
        }
        metrics.append("\n");

        metrics.append("# HELP jvm_gc_collection_count_total Total GC collection count\n");
        metrics.append("# TYPE jvm_gc_collection_count_total counter\n");
        for (GarbageCollectorMXBean gc : gcBeans) {
            String gcName = gc.getName().replace(" ", "_").toLowerCase();
            metrics.append(String.format("jvm_gc_collection_count_total{gc=\"%s\"} %d\n",
                    gcName, gc.getCollectionCount()));
        }
        metrics.append("\n");

        // Application metrics
        metrics.append("# HELP http_requests_total Total HTTP requests\n");
        metrics.append("# TYPE http_requests_total counter\n");
        metrics.append(String.format("http_requests_total{service=\"was-1-tomcat\"} %d\n\n",
                requestCounter.get()));

        metrics.append("# HELP http_errors_total Total HTTP errors\n");
        metrics.append("# TYPE http_errors_total counter\n");
        metrics.append(String.format("http_errors_total{service=\"was-1-tomcat\"} %d\n\n",
                errorCounter.get()));

        metrics.append("# HELP http_request_duration_seconds_total Total request duration\n");
        metrics.append("# TYPE http_request_duration_seconds_total counter\n");
        metrics.append(String.format("http_request_duration_seconds_total{service=\"was-1-tomcat\"} %.3f\n\n",
                requestDurationTotal.get() / 1000.0));

        // Uptime
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        metrics.append("# HELP process_uptime_seconds Process uptime in seconds\n");
        metrics.append("# TYPE process_uptime_seconds gauge\n");
        metrics.append(String.format("process_uptime_seconds %.3f\n\n", uptime / 1000.0));

        // CPU
        int processors = Runtime.getRuntime().availableProcessors();
        metrics.append("# HELP process_cpu_available Available processors\n");
        metrics.append("# TYPE process_cpu_available gauge\n");
        metrics.append(String.format("process_cpu_available %d\n", processors));

        out.print(metrics.toString());
        out.flush();
    }
}
