# **LightStudio_Done_API**
이 플러그인은 팀 라이트스튜디오 에서 제작했으며
taromati 의 done-connector 플러그인을 포크하였음을 알립니다.

## Minecraft Chzzk / Soop 후원 연동 플러그인

## **지원 버전**
Java 버전: 21

## **Google Sheet 연동**
LightStudio_Done_API 플러그인은 구글시트를 이용하여 손쉽게 유저를 추가 또는 관리 할수 있습니다.
`config.yml`에서 `SheetMode`를 true 로 설정후 서버를 재시작 하세요

구글 시트만 지원되며 구글 시트에서 다음과 같이 양식을 추가하거나 아래의 시트를 복제 하세요
https://docs.google.com/spreadsheets/d/1Lk8A28KqU0TsMIdbobfKzKCzzfKzxoxl0C7tLltcTQc/edit?gid=0#gid=0

### 주의 사항
이 기능을 활성화시 반드시 구글 시트를 이용해서 유저를 추가하세요
`config.yml`을 이용해서 추가시 데이터가 사라질수 있습니다.

## **방송상태 표시 기능**
LightStudio_Done_API 플러그인은 유저가 방송이 켜져있는지 여부를 판단할수 있습니다.
`user.yml` 에 등록되어있는 플레이어가 방송이 켜질시
`/방송켜짐 <player>` 명령어가 실행됩니다.
반대로 방송이 꺼질경우
`/방송꺼짐 <player>` 명령어가 실행됩니다.

이기능은 기본적으로 비활성화 되어있으며 `config.yml` 에서
`MarkLive` 를 true로 설정시 사용할수 있습니다.

## **api 연동현황 기능**
`/api` 명령어를 사용시 플레이어의 현제 연결되어있는 플렛폼이 출력됩니다.

## **방송 바로가기 기능**
`/방송정보 <player>` 명령어를 사용시 <player>의 현제 연결되어 있는 방송 플렛폼으로 이동할수 있는 방송 링크로 이동 됩니다.

## **사용 방법**

* 플러그인 적용 후 서버 실행시 자동으로 기능 활성화
* `/done [on|off|reconnect|reload|add|sheetreload]` 명령어로 기능 제어
* `/done on` 후원자 연동 기능 활성화
* `/done off` 후원자 연동 기능 비활성화
* `/done reconnect all` 전체 재접속
* `/done reconnect <닉네임>` 해당 닉네임 재접속, 컨피그에서 치지직/숲 바로 아래 단계의 닉네임 혹은 마크닉네임 입력, 자동완성은 마크닉네임만 지원
* `/done reload` 설정 파일 리로드 및 재접속
* `/done sheetreload` 구글 시트 데이터 다시 불러오기
* `/done add <플랫폼> <방송닉> <방송ID> <마크닉>` 도네연결 임시 추가, reload가 어려운 상황에서 임시로 연결 추가. 서버 재기동시에 없어짐
