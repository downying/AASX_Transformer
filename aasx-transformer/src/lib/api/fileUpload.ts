import axios from "axios";

export const uploadFile = async (formData: FormData) => {
  try {
    // 환경 변수 출력 (디버깅용)
    console.log("API URL:", process.env.NEXT_PUBLIC_API_URL); // API 요청 URL

    // 파일 업로드 요청 - 업로드할 데이터
    const response = await axios.post(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/aasx`, 
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" }, // 헤더 설정
      }
    );

    // 서버 응답
    const parsedData = response.data.map((item: string) => {
      try {
        // return JSON.parse(item);
        return item; 
      } catch (error) {
        console.error("JSON 파싱 오류:", error);
        return null;
      }
    });

    console.log("API - 서버 응답:", parsedData);
    return parsedData;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      // 서버 응답 오류
      console.error("서버 오류:", error.response.data);
      throw new Error(`파일 업로드 실패: ${error.response.data}`);
    } else {
      // 네트워크 오류 또는 요청 오류
      console.error("요청 오류:", error);
      throw new Error("파일 업로드 실패: 네트워크 오류");
    }
  }
};
