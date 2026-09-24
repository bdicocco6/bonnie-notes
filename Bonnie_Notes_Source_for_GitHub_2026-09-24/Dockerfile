FROM node:24-alpine

WORKDIR /app

COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev

COPY server/src ./src
RUN mkdir -p /data && chown -R node:node /app /data

USER node
EXPOSE 8787

CMD ["node", "src/index.js"]
