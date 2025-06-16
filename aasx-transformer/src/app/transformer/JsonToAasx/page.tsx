"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedJsonFiles } from "@/lib/api/fileUpload";
import {
  listAttachmentFileMetasByPackageFile,
  previewFile,
  downloadFile,
  downloadWithUrlAasx,
  downloadRevertedAasx,
} from "@/lib/api/fileDownload";

export interface FileMeta {
  aasId: string;
  submodelId: string;
  idShort: string;
  hash: string;
  contentType: string;
  extension: string;
}

const JsonToAasxPage = () => {
  // 원래 업로드된 파일 목록
  const [jsonFiles, setJsonFiles] = useState<string[]>([]);
  // 선택한 패키지 파일의 첨부파일 메타 정보 상태
  const [attachmentFileMetas, setAttachmentFileMetas] = useState<FileMeta[]>([]);
  // 현재 선택한 json 파일 (예: 목록에서 클릭한 파일)을 state로 관리
  const [selectedJsonFile, setSelectedJsonFile] = useState<string>("");

  // 업로드된 파일 이름 가져오기
  useEffect(() => {
    const loadFiles = async () => {
      try {
        // json 파일 이름 배열 (문자열) 받아오기
        const jsonFiles: string[] = await listUploadedJsonFiles();
        setJsonFiles(jsonFiles);
        // 최초에 첫번째 파일이 선택되도록 처리 (있다면)
        if (jsonFiles.length > 0) {
          setSelectedJsonFile(jsonFiles[0]);
        }
      } catch (error) {
        console.error("JSON 파일 목록을 가져오는 중 오류 발생:", error);
      }
    };
    loadFiles();
  }, []);

  // 선택된 JSON 파일이 변경되면 첨부파일 메타 정보를 불러옴
  useEffect(() => {
    const loadAttachmentMetas = async () => {
      if (!selectedJsonFile) return;
      try {
        const fileMetas: FileMeta[] = await listAttachmentFileMetasByPackageFile(selectedJsonFile);
        setAttachmentFileMetas(fileMetas);
      } catch (error) {
        console.error("첨부파일 메타 정보를 가져오는 중 오류 발생:", error);
      }
    };
    loadAttachmentMetas();
  }, [selectedJsonFile]);

  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground px-4 sm:px-6 lg:px-8">
      <div className="w-full max-w-4xl">
        {/* 홈 버튼 */}
        <div className="flex items-start mb-4">
          <Link href="/">
            <Button variant="default" className="mt-4 bg-white text-black border-black hover:bg-black hover:text-white">
              Home
            </Button>
          </Link>
        </div>

        {/* 업로드된 JSON 파일 목록 */}
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-xl sm:text-2xl">Uploaded JSON Files</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col items-center justify-center p-8 sm:p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full">
              {jsonFiles.length > 0 ? (
                <ul className="w-full text-center space-y-2">
                  {jsonFiles.map((file, idx) => {
                    const isSelected = file === selectedJsonFile;
                    return (
                      <li
                        key={idx}
                        onClick={() => setSelectedJsonFile(file)}
                        className={`cursor-pointer py-2 px-4 hover:underline min-h-[40px] rounded ${isSelected ? "bg-blue-100 font-bold" : "bg-transparent"
                          }`}
                      >
                        {file}
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <p>No uploaded JSON files found.</p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* 선택된 JSON 파일의 첨부파일 목록 */}
        <Card className="mt-8">
          <CardHeader className="text-center">
            <CardTitle className="text-xl sm:text-2xl">{selectedJsonFile}</CardTitle>
          </CardHeader>
          <CardContent>
            {attachmentFileMetas.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full table-auto border-collapse">
                  <thead>
                    <tr className="border-b">
                      <th className="text-center py-2 px-4">Hash</th>
                      <th className="text-center py-2 px-4">ContentType</th>
                      <th className="text-center py-2 px-4">Extension</th>
                      <th className="text-center py-2 px-4">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {attachmentFileMetas.map((meta, idx) => {
                      const fullHashAndExt = meta.hash + meta.extension;
                      return (
                        <tr key={idx} className="hover:bg-gray-50">
                          <td className="text-center py-2 px-4">{meta.hash}</td>
                          <td className="text-center py-2 px-4">{meta.contentType}</td>
                          <td className="text-center py-2 px-4">{meta.extension}</td>
                          <td className="py-2 px-4">
                            <div className="flex flex-col sm:flex-row justify-center gap-2">
                              <Button size="sm" variant="outline" onClick={() => previewFile(meta)}>
                                Viewer
                              </Button>
                              <Button size="sm" onClick={() => downloadFile(fullHashAndExt)}>
                                Download
                              </Button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <p>No attachment file meta available.</p>
            )}
          </CardContent>
        </Card>

        {/* AASX 패키지 다운로드 */}
        <Card className="mt-8">
          <CardHeader className="text-center">
            <CardTitle className="text-xl sm:text-2xl">Download AASX Package</CardTitle>
          </CardHeader>
          <CardContent>
            {jsonFiles.length > 0 ? (
              <div className="space-y-4">
                {jsonFiles.map((file) => (
                  <div
                    key={file}
                    className="flex flex-col md:flex-row justify-between items-center bg-white p-4 rounded shadow"
                  >
                    <span className="mb-2 md:mb-0 font-medium">{file}</span>
                    <div className="flex flex-col sm:flex-row gap-2">
                      <Button size="sm" onClick={() => downloadWithUrlAasx(file)}>
                        URL in AASX
                      </Button>
                      <Button size="sm" onClick={() => downloadRevertedAasx(file)}>
                        Revert AASX
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p>No files available for AASX download.</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};


export default JsonToAasxPage;