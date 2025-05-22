# Step 1 : Python 3.9 이미지를 기반으로 함
FROM python:3.9

# Step 2 : 작업 디렉토리 설정
WORKDIR /app/python

# Step 3: 애플리케이션 파일 복사
COPY requirements.txt requirements.txt ./

# Step 4 : 의존성 설치
RUN pip install --no-cache-dir -r requirements.txt

