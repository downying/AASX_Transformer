import axios from "axios";

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