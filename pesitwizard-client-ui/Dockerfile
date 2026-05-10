# Build stage
FROM node:26-alpine AS builder
WORKDIR /app
# Copy local dependency first (provided by CI via build context)
COPY pesitwizard-frontend-common ./pesitwizard-frontend-common
COPY package.json ./
# Rewrite file: reference to use the local copy, then install
RUN sed -i 's|\.\./pesitwizard-frontend-common|./pesitwizard-frontend-common|g' package.json \
    && npm install
COPY . .
RUN npm run build

# Runtime stage - use unprivileged nginx for Kubernetes
FROM docker.io/nginxinc/nginx-unprivileged:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Default values for environment variables
ENV NGINX_PORT=8080
ENV API_HOST=pesitwizard-client-api
ENV API_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost:8080/ || exit 1
