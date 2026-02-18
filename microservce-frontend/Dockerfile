FROM node:20-alpine AS deps
WORKDIR /app
COPY microservce-frontend/package.json microservce-frontend/package-lock.json ./
RUN npm ci

FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY microservce-frontend/ ./
COPY env/frontend.env /app/env/frontend.env
ENV NEXT_TELEMETRY_DISABLED=1
RUN set -a; . /app/env/frontend.env; set +a; npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
