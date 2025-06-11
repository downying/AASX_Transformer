import axios from "axios";
import { Environment } from "./Environment";

/**
 * .aasx 파일 업로드 → URL이 반영된 Environment 를 JSON 문자열로 받고
 * JSON.parse 해서 Environment 객체 배열로 반환
 */
export const uploadFile = async (formData: FormData): Promise<Environment[]> => {
  try {
    console.log("API URL:", process.env.NEXT_PUBLIC_API_URL);

    // 서버가 string[] 형태의 JSON 문자열을 반환합니다
    const response = await axios.post<string[]>(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/aasx`,
      formData,
      { headers: { "Content-Type": "multipart/form-data" } }
    );

    // 각 문자열을 JSON.parse 해서 Environment 로 변환
    const envs: Environment[] = response.data.map((jsonStr) => {
      try {
        return JSON.parse(jsonStr) as Environment;
      } catch (e) {
        console.error("Environment 파싱 실패:", e, jsonStr);
        throw e;
      }
    });

    console.log("API - parsed Environments:", envs);
    return envs;

  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      console.error("서버 오류:", error.response.data);
      throw new Error(`파일 업로드 실패: ${error.response.data}`);
    } else {
      console.error("네트워크 오류:", error);
      throw new Error("파일 업로드 실패: 네트워크 오류");
    }
  }
};

/**
 * 업로드된 파일 이름 목록 조회 (AASX)
 */
export const listUploadedFiles = async (): Promise<string[]> => {
  try {
    const response = await axios.get<string[]>(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/uploadedFileNames`
    );
    console.log("API - 파일 이름 목록:", response.data);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      console.error("서버 오류:", error.response.data);
      throw new Error(`파일 목록 조회 실패: ${error.response.data}`);
    } else {
      console.error("네트워크 오류:", error);
      throw new Error("파일 목록 조회 실패: 네트워크 오류");
    };
  }
}

/** 
 * JSON 파일 업로드 → AASX 패키지 2종 생성 → 파일명 리스트 반환 
 */
export const uploadJson = async (formData: FormData): Promise<string[]> => {
  try {
    const res = await axios.post<string[]>(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/json`,
      formData,
      { headers: { "Content-Type": "multipart/form-data" } }
    );

    // res.data 는 ["base-url.aasx", "base-revert.aasx"]
    console.log("uploadJson → 생성된 AASX 파일명:", res.data);
    return res.data;

  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      console.error("❌ 서버 응답 오류:", error.response.data);
      throw new Error(`업로드 실패: ${error.response.data}`);
    } else {
      console.error("❌ 네트워크 오류:", error);
      throw new Error("업로드 실패: 네트워크 오류");
    }
  }
};

/**
 * 업로드된 파일 이름 목록 조회 (JSON)
 */
export const listUploadedJsonFiles = async (): Promise<string[]> => {
  try {
    const response = await axios.get<string[]>(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/uploadedJsonFileNames`
    );
    console.log("API - JSON 파일 이름 목록:", response.data);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      console.error("서버 오류:", error.response.data);
      throw new Error(`JSON 파일 목록 조회 실패: ${error.response.data}`);
    } else {
      console.error("네트워크 오류:", error);
      throw new Error("JSON 파일 목록 조회 실패: 네트워크 오류");
    }
  }
};
