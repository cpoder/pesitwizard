# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage - use unprivileged nginx for Kubernetes
FROM docker.io/nginxinc/nginx-unprivileged:alpine
COPY --from=builder /app/.vitepress/dist /usr/share/nginx/html

EXPOSE 8080
