import { FileMeta } from "@/app/admin/file-meta/page";
import { FileEntry } from "@/app/admin/uploaded/page";
import axios from "axios";

// ✅ 특정 패키지 파일 내의 첨부파일 목록 조회 API 호출 함수
export async function listAttachmentFileMetasByPackageFile(packageFileName: string) {
  const encodedName = encodeURIComponent(packageFileName);
  try {
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/package/${encodedName}`
    );
    return response.data;
  } catch (error: any) {
    const errorMsg =
      error.response && error.response.data
        ? error.response.data
        : error.message;
    console.error("서버 에러 응답:", errorMsg);
    throw new Error("첨부파일 메타 정보를 불러오는 중 오류 발생");
  }
}

/**
 * ✅ 파일 미리보기 함수
 * @param meta 파일 메타 정보를 포함하는 객체 (해시, 확장자, contentType 등)
 */
export const previewFile = async (meta: { hash: string; extension: string; contentType: string }) => {
  try {
    const fullHashAndExt = meta.hash + meta.extension;
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/download/${fullHashAndExt}`,
      { responseType: "blob" }
    );

    // 서버에서 응답한 Blob의 타입 대신 DB에 저장된 MIME 타입을 사용
    const mimeType = meta.contentType || response.data.type || "application/octet-stream";

    const blobUrl = window.URL.createObjectURL(
      new Blob([response.data], { type: mimeType })
    );
    
    // 새 탭에서 파일 미리보기
    window.open(blobUrl, "_blank");
  } catch (error) {
    console.error("파일 미리보기 실패:", error);
    throw new Error("파일 미리보기에 실패했습니다.");
  }
};

/** 
 * ✅ 서버에서 업데이트된 Environment JSON 파일을 다운로드
 * @param fileName 확장자를 포함한 파일 이름 
 */
// downloadEnvironment.js (API 호출과 JSON 다운로드 및 처리)
export const downloadEnvironment = async (fileName: string) => {
  try {
    // JSON 데이터를 Blob 형태로 요청
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/download/environment/${fileName}`,
      { responseType: "blob" }
    );
    
    // Content-Disposition 헤더에서 파일명 추출
    const contentDisposition = response.headers["content-disposition"];
    let downloadFileName = "";
    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename="(.+?)"/);
      if (fileNameMatch && fileNameMatch[1]) {
        downloadFileName = fileNameMatch[1];
      }
    }
    if (!downloadFileName) {
      console.error("서버에서 파일명을 전달받지 못했습니다.");
      throw new Error("파일명을 받아올 수 없습니다.");
    }
    
    // Blob URL 생성 및 다운로드 트리거
    const blob = new Blob([response.data], { type: "application/json" });
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = downloadUrl;
    link.setAttribute("download", downloadFileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
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
    
    // Content-Disposition 헤더에서 파일명 추출 (없으면 기본 hash 사용)
    const contentDisposition = response.headers["content-disposition"];
    let downloadFileName = hash;
    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename="(.+?)"/);
      if (fileNameMatch && fileNameMatch[1]) {
        downloadFileName = fileNameMatch[1];
      }
    }
    
    const blobUrl = window.URL.createObjectURL(
      new Blob([response.data], { type: "application/octet-stream" })
    );
    
    const link = document.createElement("a");
    link.href = blobUrl;
    link.setAttribute("download", downloadFileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
  } catch (error) {
    console.error("파일 다운로드 실패:", error);
    throw new Error("파일 다운로드에 실패했습니다.");
  }
};

// ✅ 첨부파일 메타 삭제 API 호출
export async function deleteAttachmentMeta(compositeKey: string) {
  await axios.delete(
    `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/delete/file`,
    { params: { compositeKey } }
  );
}

// ✅ 모든 파일 메타 조회 API 호출
// offset, limit 받도록 수정
export async function fetchAllFileMetas(offset: number, limit: number): Promise<{ items: FileMeta[], totalCount: number }> {
  try {
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/file-metas`,
      { params: { offset, limit } }
    );
    return response.data; // { items, totalCount }
  } catch (error: any) {
    const errorMsg = error.response?.data?.message || error.message || "알 수 없는 오류";
    console.error("파일 메타 불러오기 실패:", errorMsg);
    throw new Error("파일 메타 정보를 불러오는 중 오류 발생");
  }
}

// ✅ 업로드 파일 조회 (offset, limit 지원)
export async function fetchUploadedFiles(offset: number, limit: number): Promise<{ items: FileEntry[], totalCount: number }> {
  try {
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/files`,
      { params: { offset, limit } }
    );
    return response.data; // { items, totalCount }
  } catch (error: any) {
    const errorMsg = error.response?.data?.message || error.message || "알 수 없는 오류";
    console.error("업로드 파일 목록 가져오기 실패:", errorMsg);
    throw new Error("업로드된 파일 목록을 불러오는 중 오류 발생");
  }
}

// ======================= AASX Download =======================

// ✅ URL 포함 AASX 패키지 다운로드
export async function downloadWithUrlAasx(fileName: string) {
  try {
    // 1) fileName이 "BALL_END_BOSS_ONE_DPP_demo_edited_v3.json" 형태로 들어오면,
    //    끝의 ".json"을 떼고 "-url.aasx"를 붙여서 정확한 AASX 파일명을 만듭니다.
    const base = fileName.replace(/\.json$/i, "");
    const aasxFile = `${base}-url.aasx`;

    // 2) 백엔드에 실제 존재하는 AASX 이름으로 GET 요청
    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/json/download/url/${encodeURIComponent(aasxFile)}`,
      { responseType: "blob" }
    );

    // 3) Content-Disposition 헤더에 담긴 실제 파일명을 가져오거나, aasxFile로 대체
    const disposition = response.headers["content-disposition"];
    let downloadName = aasxFile;
    if (disposition) {
      const m = disposition.match(/filename="(.+?)"/);
      if (m && m[1]) downloadName = m[1];
    }

    // 4) Blob으로 다운로드 트리거
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement("a");
    link.href = url;
    link.download = downloadName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (e) {
    console.error("URL AASX 다운로드 실패:", e);
    alert("URL AASX 다운로드에 실패했습니다.");
  }
}


// ✅ 상대경로 복원 AASX 패키지 다운로드
export async function downloadRevertedAasx(fileName: string) {
  try {
    const base = fileName.replace(/\.json$/i, "");
    const aasxFile = `${base}-revert.aasx`;

    const response = await axios.get(
      `${process.env.NEXT_PUBLIC_API_URL}/api/transformer/json/download/revert/${encodeURIComponent(aasxFile)}`,
      { responseType: "blob" }
    );

    const disposition = response.headers["content-disposition"];
    let downloadName = aasxFile;
    if (disposition) {
      const m = disposition.match(/filename="(.+?)"/);
      if (m && m[1]) downloadName = m[1];
    }

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement("a");
    link.href = url;
    link.download = downloadName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (e) {
    console.error("복원 AASX 다운로드 실패:", e);
    alert("복원 AASX 다운로드에 실패했습니다.");
  }
}
