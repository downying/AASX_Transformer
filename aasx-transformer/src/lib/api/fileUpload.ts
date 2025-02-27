import axios from "axios";

export const uploadFile = async (formData: FormData) => {
  try {
    // 파일 업로드 요청
    const response = await axios.post(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/aasx`, 
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      }
    );

    return response.data;
  } catch (error) {
    throw new Error("파일 업로드 실패");
  }
};
