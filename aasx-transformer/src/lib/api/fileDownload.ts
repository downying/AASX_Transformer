import axios from "axios";

/** 
 * ✅ 서버에서 업데이트된 Environment JSON 파일을 다운로드
 * @param fileName 확장자를 포함한 파일 이름 
 */
// downloadEnvironment.js (API 호출과 JSON 다운로드 및 처리)
export const downloadEnvironment = async (fileName: string) => {
  try {
    // JSON 파일 데이터를 blob 형태로 요청
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/download/environment/${fileName}`,
      { responseType: 'blob' }
    );

    // 응답 헤더에서 Content-Disposition 추출
    const contentDisposition = response.headers['content-disposition'];
    let downloadFileName = ''; // 기본 파일명 제거

    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename="(.+?)"/);
      if (fileNameMatch && fileNameMatch[1]) {
        downloadFileName = fileNameMatch[1];
      }
    }

    // 서버에서 파일명이 전달되지 않은 경우에 대한 예외 처리
    if (!downloadFileName) {
      console.error("서버에서 파일명을 전달받지 못했습니다.");
      throw new Error("파일명을 받아올 수 없습니다.");
    }

    // blob 데이터를 사용해 임시 URL 생성
    const blob = new Blob([response.data], { type: 'application/json' });
    const downloadUrl = window.URL.createObjectURL(blob);

    // 임시 a 태그 생성 후 다운로드 트리거
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.setAttribute('download', downloadFileName); // 헤더에서 추출한 파일명 사용
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // 생성된 URL 객체 해제
    window.URL.revokeObjectURL(downloadUrl);
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
