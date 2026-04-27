FROM node:22-alpine

WORKDIR /app

RUN apk add --no-cache git openssh

COPY package*.json ./
RUN npm install --omit=dev

COPY src ./src

ENV PORT=21465

EXPOSE 21465

CMD ["npm", "start"]
