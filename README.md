# KA4 NEMESIS 택시 미터기 — 원본 TaxiCluster 화면 이식 (소스 프로젝트)

2022 카니발 KA4 5W NEMESIS용 독립 실행형 Android **요금 시뮬레이터** 프로젝트입니다.

## 포함된 화면과 기능
- 원본 `TaxiCluster.java`의 **LED 말 달리기 10프레임 패턴(48×28)**을 `HorseFrames.java`에 이식. 움직일 때 속도에 따라 프레임 전환, 정지 시 정지 프레임 표시
- 원본 TaxiCluster의 **좌측 7세그먼트 요금·표시등·주행 거리/시간·운행 시작/종료** 구성 재구현
- 원본 **CAR AUDIO** 영역: MediaSession 메타데이터(곡 제목/아티스트, 재생/일시정지, 진행률), 긴 제목 스크롤, 노브 UI
- 원본 **영수증** 영역: 기본료·거리료·시간료·할증·총액 및 장식용 바코드. 운행 종료 시 음악 대신 표시하고 탭으로 닫기
- `TaxiFare.java`, `TaxiTariff.java`: 원본 중형택시 요금 계산/17개 지역 요금표 (2026-11-04 기준, 현실 운임 정확성을 보장하지 않음)
- 차량 속도/위치 데이터는 **읽기 전용**. 차량 ECU/제어 배선/순정 앱 설정은 변경하지 않음

**주의:** 원본 `TaxiCluster.java` 자체는 원본 앱의 `ClusterKit`, `Theme`, `Telemetry`, `GpsFix`, `Music`에 의존하므로 이 독립 프로젝트에서는 원본 미터기의 레이아웃과 주요 기능을 Android Canvas로 이식했습니다. `TaxiCluster.java`를 파일 단위 그대로 복사한 형태는 아닙니다. 원본과 100% 픽셀 동일/기능 동일하다고 주장하지 않습니다. 원본 택시 구역 지도(`TaxiZones/TaxiZoneData`) 기반 자동 지역 판정은 미구현입니다.

## GPS 미수신 시 차량 차속 사용
- `SpeedArbiter`: **2초 이내 차량 차속 > 4초 이내 GPS 속도 > 미수신(요금 적산 중단)**
- GPS 속도가 유효해도 최신 차량 차속이 있으면 차량 차속 우선. GPS가 끊긴 상태에서 차량 데이터가 들어오면 시간 기반으로 차속을 적분해 거리/저속요금을 산정합니다.
- 차량 차속 입력은 `me.bamtol.ka4taximeter.VEHICLE_SPEED` 브로드캐스트를 받아 `speed_kmh` 숫자 필드로 전달하는 **외부 데이터 브리지 방식**입니다.
- **KA4 5W/NEMESIS에서 실제 차량 차속을 획득하는 생산자 코드는 아직 없습니다.** 5W의 CAN 데이터 형식/벤더 서비스/접근 권한을 실차에서 확인하고 연결해야 실제 GPS 미수신시에도 차속 입력이 이어집니다. 브리지 수신 기능만으로 순정 차속이 자동 확보되는 것은 아닙니다.

### ADB로 속도 수신 기능 시험

```bash
adb shell am broadcast -a me.bamtol.ka4taximeter.VEHICLE_SPEED -p me.bamtol.ka4taximeter --ef speed_kmh 35.5
```

반복 주입할 때 2초 이내 간격으로 보내야 차량 차속으로 인식합니다. **이 명령은 계기판의 실제 차속을 읽는 명령이 아니라 시뮬레이션 입력**입니다. 운행 중 테스트하지 마세요. 현재 브로드캐스트는 인증 없이 받을 수 있으므로 신뢰하지 못하는 앱이 설치된 기기에서 실제 과금용으로 이용하면 안 됩니다.

### NEMESIS 실제 연동에 필요한 증거

- `adb shell getprop ro.build.version.release` 및 `adb shell getprop ro.build.version.sdk`
- `adb shell pm list packages | grep -Ei 'hmg|nemesis|car|vehicle'`
- GPS가 안 잡히는 상황에서, 시스템에 *읽기 가능한* 차량 속도 서비스가 있는지 확인할 수 있는 **민감 정보 제외 로그/패키지 정보**

확인된 서비스 또는 CAN 데이터 입력 API가 있어야 브리지 송신자를 작성할 수 있습니다. CAN 프레임 식별자와 배선 등을 임의로 가정하지 마세요.

## 조작
- 상단 지역 탭: 지역 순환 (운행 중에는 변경 불가). 상단 0.7초 길게 누르면 시계외 할증 수동 토글
- 왼쪽 아래 녹색 버튼: 운행 시작; 빨간 버튼: 운행 종료(우측에 영수증 표시). 이동 중 종료한 경우 정차 후 다음 주행에 자동 시작
- 우측 영수증: 탭하면 음악 화면으로 돌아감
- 음악 표시 권한: 시스템 설정의 알림 접근(Notification listener)을 허용해야 표준 Android MediaSession을 읽을 수 있음. 5W 순정 라디오가 MediaSession을 게시하지 않으면 표시 불가

## 빌드/설치

Android Studio에서 프로젝트 루트 디렉터리를 열어 Gradle 빌드하거나 JDK17+Android SDK 환경에서:

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n me.bamtol.ka4taximeter/com.veloster.vehicle.MainActivity
```

- Android API17+를 겨냥했지만 **NEMESIS 5W에서 APK 컴파일/설치/실차 동작은 검증되지 않았습니다**.
- 해당 실행 환경에 Notification listener, MediaSession 서비스, GPS provider가 존재하는지도 실차 확인이 필요합니다.
- 화면은 가로 800×480 좌표계를 기기 해상도에 맞춰 확대/축소합니다.
- 유상 운송/법정 계량·운임 청구용이 아닌 개인용 시뮬레이터입니다.
- 운행 데이터는 앱 종료 후 유지되지 않습니다.

## 출처

원본: https://github.com/sung9155/i30-pd-dashboard
세부 파일 출처: `UPSTREAM.md` 참조.
