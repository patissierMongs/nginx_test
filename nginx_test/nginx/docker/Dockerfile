# Nginx Load Balancer for WAS Test Environment
FROM nginx:1.25-alpine

# Install required tools
RUN apk add --no-cache curl openssl

# Remove default config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom configuration
COPY config/nginx.conf /etc/nginx/nginx.conf
COPY config/proxy_params /etc/nginx/proxy_params

# Create SSL directory (for future HTTPS support)
RUN mkdir -p /etc/nginx/ssl

# Create log directory
RUN mkdir -p /var/log/nginx && \
    chown -R nginx:nginx /var/log/nginx

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
