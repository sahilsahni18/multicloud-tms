# syntax=docker/dockerfile:1.7
#
# TrackFlow frontend image.
#   build:   Node 24, type-check + Vite production bundle
#   runtime: nginx (unprivileged, port 8080) serving the SPA and proxying
#            /api to the backend so the browser sees a single origin

# ---- build ----------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Empty = same origin (nginx proxies /api). Set it only if the API lives elsewhere.
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---- runtime --------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:stable-alpine

# The image renders /etc/nginx/templates/*.template with the environment at start-up.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080
EXPOSE 8080
