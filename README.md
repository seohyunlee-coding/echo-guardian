# 🌱 Echo Guardian

- 엣지/클라이언트/서비스 통합 기반 객체 탐지 및 모니터링 플랫폼

- 실시간 탐지 파이프라인(엣지), 모바일/앱 클라이언트, 그리고 웹 서비스(백엔드)를 포함한 종합 솔루션으로, YOLOv5 기반 모델을 사용해 영상/이미지에서 객체를 탐지 및 관리합니다.

## 📂 프로젝트 구성

루트 주요 구조 (중요 파일 중심):

```
echo-guardian/
├─ README.md
├─ url.txt
├─ Client_System/
│  ├─ app/ (Android 앱 모듈)
│  ├─ build.gradle.kts
│  └─ gradlew(.bat)
├─ Edge_System/
│  ├─ detect.py
	│  ├─ train.py
	│  ├─ requirements.txt
	│  └─ models/ (YOLO 관련 모델 정의)
└─ Service_System/
	 ├─ mysite/
	 │  ├─ manage.py
	 │  └─ mysite/ (Django 프로젝트)
	 └─ blog/ (예시 앱)
```

주요 파일 설명:

- `Edge_System/detect.py`: 엣지 장치에서 실시간/오프라인 탐지 실행 스크립트
- `Edge_System/train.py`: 모델 훈련 스크립트
- `Edge_System/requirements.txt`: Python 의존성
- `Client_System/app/`: Android 클라이언트 소스
- `Service_System/mysite/manage.py`: Django 관리 명령

## ⚙️ 설치 및 실행

### Python 버전 확인

```bash
python --version
```

### 1️⃣ 리포지토리 클론

```bash
git clone https://github.com/seohyunlee-coding/echo-guardian.git
cd echo-guardian
```

### 2️⃣ 엣지(Edge_System) 가상환경 및 의존성

```bash
cd Edge_System
python -m venv .venv
# Windows PowerShell
.\.venv\Scripts\Activate.ps1
# 또는 cmd
.\.venv\Scripts\activate.bat
pip install -U pip
pip install -r requirements.txt
```

### 3️⃣ Django (Service_System) 설정 및 실행

```bash
cd ../Service_System/mysite
pip install -r ../requirements.txt
python manage.py migrate
python manage.py runserver
```

기본 DB는 `sqlite3`로 설정되어 있음

### 4️⃣ Android 빌드 (Client_System)

```bash
cd ../../Client_System
./gradlew assembleDebug   # Windows: gradlew.bat assembleDebug
```

### 배포 URL

- 개발 환경: `http://localhost:8000`
- Pythonanywhere를 통해 배포 : https://cwijiq.pythonanywhere.com/

## 🚀 주요 기능

### 핵심 기능

- 실시간 또는 배치 객체 탐지(YOLOv5 기반)
- 모델 훈련 및 하이퍼파라미터 실험
- Android 클라이언트와 Django 백엔드 통합


## 🛠️ 사용 기술

- 언어: Python 3.8+ 권장
- ML: PyTorch, YOLOv5
- Web: Django (Python)
- Mobile: Android (Gradle, Java )
- 빌드 도구: Gradle
- 배포: PythonAnywhere

## 🐛 버그 / 디버그 팁

- Django 마이그레이션 오류: `python manage.py makemigrations` 후 `migrate` 실행
- Android 빌드 실패: Gradle 캐시 정리 `./gradlew clean` 후 다시 빌드


## 📚 참고 / 출처

- YOLOv5 (Ultralytics): https://github.com/ultralytics/yolov5
- PyTorch: https://pytorch.org
- Django: https://www.djangoproject.com


## 👨‍💻 프로그래머 정보

- 이름: 이서현
- 이메일: cwijiq3085@gmail.com
- GitHub: seohyunlee-coding

