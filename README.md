# AASX_Transformer

## 💥 설계

### 요구사항

1. 개요

   본 프로젝트는 AASX 패키지 파일을 업로드하여 JSON 형식으로 변환하고, 첨부파일을 해시 기반으로 탐색하여 다운로드 링크를 제공하는 기능을 제공합니다. 이를 통해 AASX 파일 내 중복된 첨부파일을 최소화하여 효율적으로 처리하고, 이를 URL로 변환하여 관리 및 접근을 용이하게 만듭니다.

### 시스템 구조

<ol>
  <li>플로우 차트</li>
  <img src="https://github.com/user-attachments/assets/d1f9777c-4a8d-4d4d-806f-23d6b062191f">
</ol>

### 사용 언어/라이브러리

- **Backend (Java)**
  - Spring Boot
  - [aas4j](https://github.com/eclipse-aas4j/aas4j)
- **Frontend (TypeScript)**
  - [Next.js](https://nextjs.org/)
  - [Shadcn UI](https://ui.shadcn.com/)
- **사용 DB**
  - (추가 필요)
- **개발 도구**
  - VS Code
- **형상 관리**
  - [GitHub Repository](https://github.com/downying/AASX-Transformer.git)

---

## 💥 프로젝트 생성

### Spring Boot

<ol>
  <li>JDK-21 설치 (필수)
    <ul>
      <li>[JDK-21 다운로드](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)</li>
      <li>Java SE Development Kit 21.0.5 (Windows x64 Installer)</li>
      <li>시스템 환경 변수 설정
        <ul>
          <li><code>JAVA_HOME = ~/Java/JDK-17</code></li>
        </ul>
      </li>
    </ul>
  </li>
  <li>Gradle 설치 (선택)
    <ul>
      <li>[Gradle 다운로드](https://gradle.org/releases/)</li>
      <li>버전: v8.12.1</li>
      <li>설치 후 시스템 환경 변수 설정
        <ul>
          <li><code>GRADLE_HOME = ~/gradle-x.x</code></li>
          <li><code>path = C:\Program Files\gradle\gradle-8.12.1-all\gradle-8.12.1\bin</code></li>
        </ul>
      </li>
    </ul>
  </li>
  <li>VS Code Spring Boot 확장 설치
    <ul>
      <li>Spring Boot Extension Pack</li>
      <li>Spring Boot Developer Extension Pack</li>
      <li>Spring Code Generator</li>
      <li>Gradle for Java</li>
    </ul>
  </li>
  <li>프로젝트 생성
    <pre><code>Create Spring Initializer</code></pre>
    <ul>
      <li>version : 3.x.x</li>
      <li>language : Java</li>
      <li>Group Id : <code>com.aasx</code></li>
      <li>Artifact Id : <code>transformer</code></li>
      <li>Packaging Type : War</li>
      <li>Java Version : 17</li>
      <li>Dependencies
        <ul>
          <li>Spring Web</li>
          <li>Spring Boot DevTools</li>
          <li>(추후 추가 예정)</li>
        </ul>
      </li>
    </ul>
  </li>
  <li>서버 실행
    <ul>
      <li>Spring Boot Dashboard 사용</li>
    </ul>
  </li>
</ol>



### Next.js

<ol>
  <li>프로젝트 생성
    <pre><code>npx create-next-app@latest aasx-transformer</code></pre>
  </li>
  <li>서버 실행
    <pre><code>npm run dev</code></pre>
    <pre><code>http://localhost:3000/</code></pre>
  </li>
</ol>

---

## 💥 AASX to JSON (Phase 1)

### Back-end

<ol>
  <li>API를 통해 AASX 패키지 파일 업로드
    <ul>
      <li>API URL : <code>/api/transformer/aasx</code></li>
    </ul>
  </li>
  <li>AASX 패키지 파일 역직렬화 : 외부 파일의 데이터를 프로그램 내 object로 읽어옴 (aas4j 사용)</li>
  <li>AASX 내 XML 정보 모델을 JSON 정보 모델로 변환 (aas4j 사용)
    <ul>
      <li>혹은 JSON으로 나중에 바꿔도 됨</li>
    </ul>
  </li>
  <li>File SubmodelElement 탐색
    <ul>
      <li>SME에 저장된 첨부파일의 위치 확인 (상대 경로)</li>
      <li>첨부파일의 SHA256 해싱 값 계산 (해싱 알고리즘 라이브러리 사용)</li>
      <li>첨부파일 해싱 값을 기반으로 동일 파일 검색
        <ul>
          <li>동일 파일 ❌ : 첨부파일의 이름을 해싱 값으로 변경하여 저장</li>
          <li>해싱값은 다르지만 파일 이름이 같은 경우 방지</li>
          <li>해당 해싱 값으로 파일 다운로드 URL 구성</li>
        </ul>
      </li>
    </ul>
  </li>
</ol>



### Front-end

<ol>
  <li>AASX-to-JSON 테스트 화면</li>
  <li>첨부파일 탐색 (Phase 1)
    <ul>
      <li>해시 기반 검색</li>
      <li>첨부파일 다운로드</li>
    </ul>
  </li>
</ol>


<br/>


