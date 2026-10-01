# Image du frontend : build Vite puis service statique par Nginx, qui relaie /api vers le backend.
FROM node:22-alpine AS build
# Options Node facultatives pour npm (ex. --tls-max-v1.2 sur un réseau qui bloque TLS 1.3).
ARG NODE_OPTIONS=""
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
