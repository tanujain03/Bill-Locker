# ---- Build stage ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Public build-time settings (never secrets — they end up in the JS bundle).
ARG VITE_API_BASE_URL=/api
ARG VITE_API_MOCKING=false
ARG VITE_SHOW_DEMO_LOGIN=true
ARG VITE_MAX_UPLOAD_MB=10
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_API_MOCKING=$VITE_API_MOCKING \
    VITE_SHOW_DEMO_LOGIN=$VITE_SHOW_DEMO_LOGIN \
    VITE_MAX_UPLOAD_MB=$VITE_MAX_UPLOAD_MB

RUN npm run build

# ---- Runtime stage --------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Where nginx forwards /api (the backend service in docker compose).
ENV BACKEND_URL=http://backend:8080

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
