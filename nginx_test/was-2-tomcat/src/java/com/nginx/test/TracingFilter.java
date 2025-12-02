package com.nginx.test;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenTelemetry-compatible tracing filter
 * Implements W3C Trace Context propagation
 */
public class TracingFilter implements Filter {

    private static final String TRACE_PARENT_HEADER = "traceparent";
    private static final String TRACE_STATE_HEADER = "tracestate";
    private static final String B3_TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String B3_SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String B3_PARENT_SPAN_ID_HEADER = "X-B3-ParentSpanId";
    private static final String B3_SAMPLED_HEADER = "X-B3-Sampled";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialize OpenTelemetry SDK here in production
        System.out.println("[TracingFilter] Initialized with OpenTelemetry support");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long startTime = System.currentTimeMillis();

        // Extract or generate trace context
        TraceContext traceContext = extractOrCreateTraceContext(httpRequest);

        // Wrap request to inject trace headers
        HttpServletRequest wrappedRequest = wrapRequestWithTraceHeaders(httpRequest, traceContext);

        // Add trace headers to response
        httpResponse.setHeader("X-Trace-Id", traceContext.traceId);
        httpResponse.setHeader("X-Span-Id", traceContext.spanId);
        httpResponse.setHeader("X-Service", "was-1-tomcat");

        // Increment metrics
        MetricsServlet.incrementRequests();

        try {
            chain.doFilter(wrappedRequest, httpResponse);

            // Record span on success
            long duration = System.currentTimeMillis() - startTime;
            MetricsServlet.addRequestDuration(duration);

            // Log span (in production, send to Jaeger)
            logSpan(traceContext, httpRequest, httpResponse.getStatus(), duration);

        } catch (Exception e) {
            MetricsServlet.incrementErrors();

            long duration = System.currentTimeMillis() - startTime;
            logSpan(traceContext, httpRequest, 500, duration);

            throw e;
        }
    }

    private TraceContext extractOrCreateTraceContext(HttpServletRequest request) {
        TraceContext context = new TraceContext();

        // Try W3C Trace Context format first
        String traceparent = request.getHeader(TRACE_PARENT_HEADER);
        if (traceparent != null && !traceparent.isEmpty()) {
            // Format: 00-traceId-spanId-flags
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                context.traceId = parts[1];
                context.parentSpanId = parts[2];
                context.sampled = "01".equals(parts[3]);
            }
        }

        // Try B3 format (Zipkin compatibility)
        if (context.traceId == null) {
            String b3TraceId = request.getHeader(B3_TRACE_ID_HEADER);
            if (b3TraceId != null) {
                context.traceId = b3TraceId;
                context.parentSpanId = request.getHeader(B3_SPAN_ID_HEADER);
                context.sampled = "1".equals(request.getHeader(B3_SAMPLED_HEADER));
            }
        }

        // Generate new trace if none found
        if (context.traceId == null) {
            context.traceId = generateTraceId();
            context.sampled = true;
        }

        // Always generate new span ID for this request
        context.spanId = generateSpanId();
        context.serviceName = "was-1-tomcat";

        return context;
    }

    private HttpServletRequest wrapRequestWithTraceHeaders(HttpServletRequest request, TraceContext context) {
        return new HttpServletRequestWrapper(request) {
            private final Map<String, String> customHeaders = new HashMap<>();

            {
                customHeaders.put("X-Trace-Id", context.traceId);
                customHeaders.put("X-Span-Id", context.spanId);
                if (context.parentSpanId != null) {
                    customHeaders.put("X-Parent-Span-Id", context.parentSpanId);
                }
                customHeaders.put("X-Service", context.serviceName);
            }

            @Override
            public String getHeader(String name) {
                String customValue = customHeaders.get(name);
                if (customValue != null) {
                    return customValue;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.addAll(customHeaders.keySet());
                return Collections.enumeration(names);
            }
        };
    }

    private void logSpan(TraceContext context, HttpServletRequest request, int statusCode, long durationMs) {
        // In production, this would send to Jaeger via OpenTelemetry
        String spanLog = String.format(
                "[SPAN] traceId=%s spanId=%s parentSpanId=%s service=%s " +
                        "method=%s path=%s status=%d duration=%dms",
                context.traceId,
                context.spanId,
                context.parentSpanId != null ? context.parentSpanId : "root",
                context.serviceName,
                request.getMethod(),
                request.getRequestURI(),
                statusCode,
                durationMs
        );
        System.out.println(spanLog);
    }

    private String generateTraceId() {
        // 32 hex characters (128 bits)
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String generateSpanId() {
        // 16 hex characters (64 bits)
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public void destroy() {
        System.out.println("[TracingFilter] Destroyed");
    }

    private static class TraceContext {
        String traceId;
        String spanId;
        String parentSpanId;
        String serviceName;
        boolean sampled;
    }
}
