package com.nginx.test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;

public class HealthCheckServlet extends HttpServlet {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "was-1-tomcat");
        health.put("type", "legacy-onpremise");
        health.put("timestamp", Instant.now().toString());

        try {
            health.put("hostname", InetAddress.getLocalHost().getHostName());
            health.put("ip", InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            health.put("hostname", "unknown");
            health.put("ip", "unknown");
        }

        // JVM Metrics
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Map<String, Object> jvm = new HashMap<>();
        jvm.put("uptime_ms", runtimeMXBean.getUptime());
        jvm.put("heap_used_mb", memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        jvm.put("heap_max_mb", memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        jvm.put("non_heap_used_mb", memoryMXBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        jvm.put("available_processors", Runtime.getRuntime().availableProcessors());
        health.put("jvm", jvm);

        // Dependencies health
        Map<String, Object> dependencies = new HashMap<>();
        dependencies.put("redis", checkRedisHealth());
        dependencies.put("kafka", checkKafkaHealth());
        dependencies.put("jaeger", checkJaegerHealth());
        health.put("dependencies", dependencies);

        // OpenTelemetry trace context
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null) {
            health.put("traceId", traceId);
        }

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(health));
        out.flush();
    }

    private Map<String, Object> checkRedisHealth() {
        Map<String, Object> status = new HashMap<>();
        try {
            // Simulated Redis health check
            status.put("status", "UP");
            status.put("cluster", "redis-cluster:6379");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkKafkaHealth() {
        Map<String, Object> status = new HashMap<>();
        try {
            status.put("status", "UP");
            status.put("brokers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }

    private Map<String, Object> checkJaegerHealth() {
        Map<String, Object> status = new HashMap<>();
        try {
            status.put("status", "UP");
            status.put("endpoint", "http://jaeger:14268/api/traces");
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
}
