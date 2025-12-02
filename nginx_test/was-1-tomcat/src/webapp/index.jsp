<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.InetAddress" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>WAS-1 Tomcat</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); max-width: 800px; margin: 0 auto; }
        h1 { color: #d4421a; }
        .info { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 20px 0; }
        .endpoint { background: #e7f3ff; padding: 10px; margin: 5px 0; border-radius: 3px; font-family: monospace; }
        .status { color: #28a745; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #f8f9fa; }
    </style>
</head>
<body>
    <div class="container">
        <h1>WAS-1: Apache Tomcat (Legacy On-Premise)</h1>

        <div class="info">
            <strong>Server Type:</strong> Classic Tomcat 9 with JSP/Servlet<br>
            <strong>Deployment:</strong> On-Premise (systemd managed)<br>
            <strong>Status:</strong> <span class="status">RUNNING</span>
        </div>

        <h2>Server Information</h2>
        <table>
            <tr><th>Property</th><th>Value</th></tr>
            <tr><td>Hostname</td><td><%= InetAddress.getLocalHost().getHostName() %></td></tr>
            <tr><td>IP Address</td><td><%= InetAddress.getLocalHost().getHostAddress() %></td></tr>
            <tr><td>Server Time</td><td><%= LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) %></td></tr>
            <tr><td>JVM Version</td><td><%= System.getProperty("java.version") %></td></tr>
            <tr><td>Tomcat Version</td><td><%= application.getServerInfo() %></td></tr>
            <tr><td>Session ID</td><td><%= session.getId() %></td></tr>
        </table>

        <h2>Available Endpoints</h2>
        <div class="endpoint">GET /health - Health check endpoint</div>
        <div class="endpoint">GET /metrics - Prometheus metrics</div>
        <div class="endpoint">GET /api/info - Server information (JSON)</div>
        <div class="endpoint">POST /api/message - Send message to Kafka</div>
        <div class="endpoint">GET /api/cache/{key} - Get from Redis</div>
        <div class="endpoint">PUT /api/cache/{key} - Set to Redis</div>

        <h2>Integration Status</h2>
        <table>
            <tr><th>Service</th><th>Status</th><th>Endpoint</th></tr>
            <tr><td>Redis</td><td id="redis-status">Checking...</td><td>redis-cluster:6379</td></tr>
            <tr><td>Kafka</td><td id="kafka-status">Checking...</td><td>kafka:9092</td></tr>
            <tr><td>Jaeger</td><td id="jaeger-status">Checking...</td><td>jaeger:14268</td></tr>
        </table>
    </div>

    <script>
        // Client-side status check simulation
        setTimeout(() => {
            document.getElementById('redis-status').innerHTML = '<span style="color:green">Connected</span>';
            document.getElementById('kafka-status').innerHTML = '<span style="color:green">Connected</span>';
            document.getElementById('jaeger-status').innerHTML = '<span style="color:green">Connected</span>';
        }, 1000);
    </script>
</body>
</html>
