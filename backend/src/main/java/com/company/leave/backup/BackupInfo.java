package com.company.leave.backup;

/**
 * 백업 정보 파일(백업과 같은 이름의 .json). 복원 전에 손상·변조(체크섬)와 DB 버전을 확인하는 데 쓴다.
 *
 * @param createdAt 만든 시각(ISO, 한국 시간)
 * @param kind      manual / auto / pre-restore
 * @param dbVersion 백업할 때의 앱 DB 버전(Flyway, 예: "9")
 * @param size      백업 파일 크기(바이트)
 * @param sha256    백업 파일 SHA-256(16진수 소문자)
 */
public record BackupInfo(String fileName, String createdAt, String kind, String dbVersion, long size, String sha256) {
}
