# Image du frontend SmartFood : fichiers statiques + relais de /api et /ws vers le backend
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
# Variables attendues : PORT (fournie par l'hebergeur) et BACKEND_URL (ex. https://mon-backend.up.railway.app)
ENV PORT=80
