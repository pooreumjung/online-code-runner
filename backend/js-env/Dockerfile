# Step 1: Node.js LTS 버전 기반
FROM node:20

# Step 2: 작업 디렉토리
WORKDIR /app/js

# Step 3: package.json만 복사해서 의존성 설치
COPY package.json package-lock.json ./

# Step 4: 의존성 설치
RUN npm ci --omit=dev  # 또는 npm install


