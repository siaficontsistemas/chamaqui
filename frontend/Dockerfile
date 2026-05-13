FROM node:20-alpine AS build

WORKDIR /app

COPY frontend/package*.json ./frontend/

WORKDIR /app/frontend
RUN npm ci

WORKDIR /app
COPY frontend ./frontend
COPY .env.production ./.env.production

WORKDIR /app/frontend
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html

EXPOSE 3000
