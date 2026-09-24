FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY src ./src
RUN mkdir -p /app/data && chown -R node:node /app
USER node
EXPOSE 8787
CMD ["node", "src/index.js"]

