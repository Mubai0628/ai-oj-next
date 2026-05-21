FROM node:24-alpine AS build
ARG APP_WORKSPACE
WORKDIR /workspace
COPY package.json package-lock.json* ./
COPY apps ./apps
COPY packages ./packages
COPY tsconfig.base.json ./
RUN npm install
RUN npm run build -w ${APP_WORKSPACE}

FROM nginx:1.27-alpine
ARG APP_DIR
COPY --from=build /workspace/apps/${APP_DIR}/dist /usr/share/nginx/html
COPY deploy/nginx/spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

