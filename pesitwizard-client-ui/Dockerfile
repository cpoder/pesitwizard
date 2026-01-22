# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
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
