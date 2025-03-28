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
    <div className="flex flex-col items-center w-full h-full p-8">
      <Link href="/">
        <Button variant="outline">Home</Button>
      </Link>

      <Card className="w-full mt-6">
        <CardHeader className="text-center">
          <CardTitle>Uploaded AASX Files</CardTitle>
        </CardHeader>
        <CardContent>
          {uploadedFiles.length > 0 ? (
            <ul>
              {uploadedFiles.map((file, idx) => (
                <li key={idx}>{file}</li>
              ))}
            </ul>
          ) : (
            <p>No uploaded files found.</p>
          )}
        </CardContent>
      </Card>

      <Card className="w-full mt-6">
        <CardHeader>
          <CardTitle>Download Environment JSON</CardTitle>
        </CardHeader>
        <CardContent>
          {uploadedFiles.length > 0 ? (
            uploadedFiles.map((file) => (
              <div key={file} className="flex justify-between items-center my-2">
                <span>{file}</span>
                <Button onClick={() => downloadEnvironment(file)} size="sm">
                  Download Environment JSON
                </Button>
              </div>
            ))
          ) : (
            <p>No files available for download.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default TransformerPage;
