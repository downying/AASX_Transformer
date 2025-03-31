"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles } from "@/lib/api/fileUpload";
import { downloadEnvironment } from "@/lib/api/fileDownload";

const TransformerPage = () => {
  const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);

  // 업로드된 파일 목록 로드
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
        {/* 홈으로 돌아가는 링크 */}
        <div className="flex items-start mb-4">
          <Link href="/">
            <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white" variant="default">
              Home
            </Button>
          </Link>
        </div>

        {/* 컨테이너 */}
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Uploaded AASX Files
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className={`flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl `}>
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
  )
};

export default TransformerPage;
