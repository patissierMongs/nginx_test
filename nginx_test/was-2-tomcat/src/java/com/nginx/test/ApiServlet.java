package com.nginx.test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.JedisCluster;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class ApiServlet extends HttpServlet {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JedisCluster jedisCluster;
    private KafkaProducer<String, String> kafkaProducer;

    @Override
    public void init() throws ServletException {
        super.init();
        // Initialize Redis and Kafka connections in real implementation
        // jedisCluster = RedisConfig.getCluster();
        // kafkaProducer = KafkaConfig.getProducer();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Map<String, Object> result = new HashMap<>();

        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/info")) {
            // GET /api/info - Server information
            result = getServerInfo(request);
        } else if (pathInfo.startsWith("/cache/")) {
            // GET /api/cache/{key} - Get from Redis
            String key = pathInfo.substring(7);
            result = getFromCache(key);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            result.put("error", "Endpoint not found");
            result.put("path", pathInfo);
        }

        // Add trace context
        addTraceContext(request, result);

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Map<String, Object> result = new HashMap<>();

        if (pathInfo != null && pathInfo.equals("/message")) {
            // POST /api/message - Send message to Kafka
            String body = readRequestBody(request);
            result = sendToKafka(body);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            result.put("error", "Endpoint not found");
        }

        addTraceContext(request, result);

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        Map<String, Object> result = new HashMap<>();

        if (pathInfo != null && pathInfo.startsWith("/cache/")) {
            // PUT /api/cache/{key} - Set to Redis
            String key = pathInfo.substring(7);
            String body = readRequestBody(request);
            result = setToCache(key, body);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            result.put("error", "Endpoint not found");
        }

        addTraceContext(request, result);

        PrintWriter out = response.getWriter();
        out.print(gson.toJson(result));
        out.flush();
    }

    private Map<String, Object> getServerInfo(HttpServletRequest request) {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "was-1-tomcat");
        info.put("type", "legacy-onpremise");
        info.put("framework", "Tomcat 9 + Servlet");
        info.put("timestamp", Instant.now().toString());

        try {
            info.put("hostname", InetAddress.getLocalHost().getHostName());
            info.put("ip", InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            info.put("hostname", "unknown");
        }

        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("remoteAddr", request.getRemoteAddr());
        requestInfo.put("method", request.getMethod());
        requestInfo.put("uri", request.getRequestURI());
        requestInfo.put("protocol", request.getProtocol());
        info.put("request", requestInfo);

        return info;
    }

    private Map<String, Object> getFromCache(String key) {
        Map<String, Object> result = new HashMap<>();
        result.put("operation", "GET");
        result.put("key", key);

        try {
            // Simulated Redis get
            // String value = jedisCluster.get(key);
            String value = "simulated_value_for_" + key;
            result.put("value", value);
            result.put("source", "redis-cluster");
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> setToCache(String key, String value) {
        Map<String, Object> result = new HashMap<>();
        result.put("operation", "SET");
        result.put("key", key);
        result.put("value", value);

        try {
            // Simulated Redis set
            // jedisCluster.set(key, value);
            result.put("success", true);
            result.put("destination", "redis-cluster");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> sendToKafka(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("operation", "PUBLISH");
        result.put("topic", "nginx-test-events");
        result.put("message", message);

        try {
            String messageId = UUID.randomUUID().toString();
            // Simulated Kafka send
            // kafkaProducer.send(new ProducerRecord<>("nginx-test-events", messageId, message));
            result.put("messageId", messageId);
            result.put("success", true);
            result.put("broker", "kafka-cluster");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void addTraceContext(HttpServletRequest request, Map<String, Object> result) {
        String traceId = request.getHeader("X-Trace-Id");
        String spanId = request.getHeader("X-Span-Id");
        String parentSpanId = request.getHeader("X-Parent-Span-Id");

        if (traceId != null) {
            Map<String, String> trace = new HashMap<>();
            trace.put("traceId", traceId);
            if (spanId != null) trace.put("spanId", spanId);
            if (parentSpanId != null) trace.put("parentSpanId", parentSpanId);
            result.put("trace", trace);
        }
    }
}
