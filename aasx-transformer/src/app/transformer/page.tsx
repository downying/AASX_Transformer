"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles } from "@/lib/api/fileUpload";
import { downloadEnvironment } from "@/lib/api/fileDownload";

const TransformerPage = () => {
  // 원래 업로드된 파일 목록
  const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);

  // 업로드된 파일 이름 가져오기
  useEffect(() => {
    const loadFiles = async () => {
      try {
        const files = await listUploadedFiles();
        setUploadedFiles(files);
      } catch (error) {
        console.error("파일 목록을 가져오는 중 오류 발생:", error);
      }
    };
    loadFiles();
  }, []);

  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      <div className="w-full max-w-4xl">
        {/* 홈 버튼 */}
        <div className="flex items-start mb-4">
          <Link href="/">
            <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white">
              Home
            </Button>
          </Link>
        </div>

        {/* 업로드된 파일 목록 */}
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Uploaded AASX Files
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl">
              {uploadedFiles.length > 0 ? (
                <ul>
                  {uploadedFiles.map((file, idx) => (
                    <li key={idx}>{file}</li>
                  ))}
                </ul>
              ) : (
                <p>No uploaded files found.</p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* File List UI 추가 */}
        <Card className="mt-12">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">File List</CardTitle>
          </CardHeader>
          <CardContent>
            <table className="w-full table-auto border-collapse">
              <thead>
                <tr className="border-b">
                  <th className="text-center py-2">Hash</th>
                  <th className="text-center py-2">Content Type</th>
                  <th className="text-center py-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {/* 수정 필요 */}
                {[1, 2, 3].map((_, idx) => (
                  <tr key={idx} className="border-b">
                    <td className="text-center py-2">123abc...{idx}</td>
                    <td className="text-center py-2">image/png</td>
                    <td className="py-2">
                      <div className="flex justify-center gap-2">
                        <Button size="sm" variant="outline">
                          Viewer
                        </Button>
                        <Button size="sm">
                          Download
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>

        {/* 환경 JSON 다운로드 */}
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
              <p>No files available for download.</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default TransformerPage;
