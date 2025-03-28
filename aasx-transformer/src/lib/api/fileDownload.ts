import axios from "axios";

/**
 * 서버에서 업데이트된 Environment JSON 파일을 다운로드합니다.
 * @param fileName 확장자를 포함한 파일 이름 (예: example.aasx)
 */
// downloadEnvironment.js (API 호출과 JSON 다운로드 및 처리)
export const downloadEnvironment = async (fileName: string) => {
    try {
      // JSON 파일 다운로드
      const response = await fetch(`/api/transformer/download/environment/${fileName}`);
      const json = await response.json();
  
      // JSON에서 URL 추출 (예: json.file.url)
      const downloadUrl = json.file.url;
  
      // URL로 파일 자동 다운로드
      if (downloadUrl) {
        window.location.href = downloadUrl;
      } else {
        console.error("URL이 포함되지 않은 JSON 파일입니다.");
      }
    } catch (error) {
      console.error("환경 JSON 다운로드 중 오류 발생:", error);
    }
  };
  

// ✅ 파일 다운로드
export const downloadFile = async (hash: string) => {
    try {
        const response = await axios.get(
            `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/download/${hash}`,
            { responseType: "blob" }
        );
        // Blob 객체로부터 URL 생성
        const url = window.URL.createObjectURL(new Blob([response.data], { type: "application/octet-stream" }));

        // 임시 a 태그 생성 후 다운로드 트리거
        const link = document.createElement("a");
        link.href = url;
        // 파일명은 현재 해시값 그대로 사용 (Phase 2에서 확장자 등 보완 가능)
        link.setAttribute("download", hash);
        document.body.appendChild(link);
        link.click();
        // 다운로드 후 a 태그 제거
        link.parentNode?.removeChild(link);
    } catch (error) {
        console.error("파일 다운로드 실패:", error);
        throw new Error("파일 다운로드에 실패했습니다.");
    }
};
