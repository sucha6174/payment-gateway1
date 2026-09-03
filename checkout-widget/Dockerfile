FROM node:20-alpine
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY webpack.config.js ./
COPY src ./src
COPY public ./public
COPY server.js ./

RUN npm run build

EXPOSE 3001
CMD ["node", "server.js"]
