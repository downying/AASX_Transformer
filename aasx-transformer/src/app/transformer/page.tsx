"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles } from "@/lib/api/fileUpload";
// packageFileName을 인자로 받도록 변경된 listAttachmentFileMetas 함수
import { deleteAttachmentMeta, downloadEnvironment, downloadFile, listAttachmentFileMetasByPackageFile, previewFile } from "@/lib/api/fileDownload";

// 코드의 가독성과 유지보수성
export interface FileMeta {
  aasId: string;
  submodelId: string;
  idShort: string;
  hash: string;
  contentType: string;
  extension: string;
  // 추가 정보가 있으면 확장
}

const TransformerPage = () => {
  // 원래 업로드된 파일 목록
  const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);
  // 선택한 패키지 파일의 첨부파일 메타 정보 상태
  const [attachmentFileMetas, setAttachmentFileMetas] = useState<FileMeta[]>([]);
  // 현재 선택한 패키지 파일 (예: 목록에서 클릭한 파일)을 state로 관리
  const [selectedPackageFile, setSelectedPackageFile] = useState<string>("");

  // 업로드된 파일 이름 가져오기
  useEffect(() => {
    const loadFiles = async () => {
      try {
        // 패키지 파일 이름 배열 (문자열) 받아오기
        const packageFiles: string[] = await listUploadedFiles();
        setUploadedFiles(packageFiles);
        // 최초에 첫번째 파일이 선택되도록 처리 (있다면)
        if (packageFiles.length > 0) {
          setSelectedPackageFile(packageFiles[0]);
        }
      } catch (error) {
        console.error("패키지 파일 목록을 가져오는 중 오류 발생:", error);
      }
    };
    loadFiles();
  }, []);

  // 선택된 패키지 파일이 변경되면 첨부파일 메타 정보를 불러옴
  useEffect(() => {
    const loadAttachmentMetas = async () => {
      if (!selectedPackageFile) return;
      try {
        const fileMetas: FileMeta[] = await listAttachmentFileMetasByPackageFile(selectedPackageFile);
        setAttachmentFileMetas(fileMetas);
      } catch (error) {
        console.error("첨부파일 메타 정보를 가져오는 중 오류 발생:", error);
      }
    };
    loadAttachmentMetas();
  }, [selectedPackageFile]);

  // 삭제 핸들러
  const handleDelete = async (meta: FileMeta) => {
    // 1) 삭제 전 확인
    const confirmed = window.confirm("정말로 삭제하시겠습니까?");
    if (!confirmed) {
      return; // 취소하면 바로 종료
    }

    // 2) 서버에 삭제 요청
    try {
      await deleteAttachmentMeta(
        `${meta.aasId}::${meta.submodelId}::${meta.idShort}`
      );
      // 3) 삭제 후, 최신 목록을 가져와서 화면 갱신
      const updated = await listAttachmentFileMetasByPackageFile(
        selectedPackageFile
      );
      setAttachmentFileMetas(updated);
    } catch (e) {
      console.error("삭제 실패:", e);
      alert("삭제 중 오류가 발생했습니다.");
    }
  };


  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      <div className="max-w-screen-xl">
        {/* 홈 버튼 */}
        <div className="flex items-start mb-4">
          <Link href="/">
            <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white">
              Home
            </Button>
          </Link>
        </div>

        {/* 업로드된 패키지 파일 목록 */}
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Uploaded AASX Files
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl">
              {uploadedFiles.length > 0 ? (
                <ul className="w-full text-center">
                  {uploadedFiles.map((file, idx) => {
                    const isSelected = file === selectedPackageFile;
                    return (
                      <li
                        key={idx}
                        onClick={() => setSelectedPackageFile(file)}
                        className={`cursor-pointer py-2 px-4 hover:underline min-h-[40px] ${isSelected ? "bg-blue-100 font-bold" : "bg-transparent"
                          }`}
                      >
                        {file}
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <p>No uploaded AASX files found.</p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* 선택된 패키지 파일의 첨부파일 목록 – DB의 파일 메타 정보를 기반 */}
        <Card className="mt-12">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">
              {selectedPackageFile}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {attachmentFileMetas.length > 0 ? (
              <table className="w-full table-auto border-collapse">
                <thead>
                  <tr className="border-b">
                    <th className="text-center py-2 px-4">Hash</th>
                    <th className="text-center py-2 px-4">ContentType</th>
                    <th className="text-center py-2 px-4">Extension</th>
                    <th className="text-center py-2 px-4">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {attachmentFileMetas.map((meta, idx) => {
                    // meta.hash와 meta.extension을 결합하여 전체 파일명 문자열 생성
                    const fullHashAndExt = meta.hash + meta.extension;
                    return (
                      <tr key={idx} className="border-b">
                        <td className="text-center py-2 px-4">{meta.hash}</td>
                        <td className="text-center py-2 px-4">{meta.contentType}</td>
                        <td className="text-center py-2 px-4">{meta.extension}</td>
                        <td className="py-2 px-4">
                          <div className="flex justify-center gap-2">
                            <Button size="sm" variant="outline" onClick={() => previewFile(meta)}>
                              Viewer
                            </Button>
                            <Button size="sm" onClick={() => downloadFile(fullHashAndExt)}>
                              Download
                            </Button>
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => handleDelete(meta)}
                            >
                              Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <p>No attachment file meta available for this package.</p>
            )}
          </CardContent>
        </Card>

        {/* Environment JSON 다운로드 (패키지 파일 관련) */}
        <Card className="mt-12">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Download Environment JSON
            </CardTitle>
          </CardHeader>
          <CardContent>
            {uploadedFiles.length > 0 ? (
              uploadedFiles.map((file) => (
                <div key={file} className="flex justify-between items-center my-2">
                  <span>{file}</span>
                  <Button onClick={() => downloadEnvironment(file)} size="sm">
                    Download to JSON
                  </Button>
                </div>
              ))
            ) : (
              <p>No files available for JSON download.</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default TransformerPage;
