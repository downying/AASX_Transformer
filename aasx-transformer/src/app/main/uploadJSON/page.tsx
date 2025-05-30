"use client";

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { uploadJson } from '@/lib/api/fileUpload';

const UploadJSON = () => {
  const router = useRouter();
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const isJson = (file: File) => file.name.toLowerCase().endsWith('.json');

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!event.target.files) return;
    const incoming = Array.from(event.target.files);
    const bad = incoming.find(f => !isJson(f));
    if (bad) {
      alert('JSON 파일이 아닙니다.');
      return;
    }
    setSelectedFiles(prev => [...prev, ...incoming]);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => setIsDragging(false);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);

    if (e.dataTransfer.files.length === 0) return;
    const filesArray = Array.from(e.dataTransfer.files);

    const bad = filesArray.find(f => !isJson(f));
    if (bad) {
      alert('JSON 파일이 아닙니다.');
      return;
    }

    setSelectedFiles(prev => [...prev, ...filesArray]);
  };

  useEffect(() => {
    if (selectedFiles.length === 0 && fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, [selectedFiles]);

  const handleUpload = async () => {
    if (selectedFiles.length === 0) {
      alert("파일을 선택하세요.");
      return;
    }

    const formData = new FormData();
    selectedFiles.forEach(file => formData.append('files', file));
    console.log("formData : ", formData);

    try {
      const response = await uploadJson(formData);

      console.log("서버 응답: ", response);

      alert("업로드 성공");

      setSelectedFiles([]);

      router.push("/transformer/JsonToAasx");
    } catch (error) {
      console.error("업로드 오류:", error);
      alert("업로드 실패");
    }
  };

  const handleFileClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (fileInputRef.current) {
      fileInputRef.current.click(); // 파일 선택 창을 클릭하게 만듦
    }
  };

  const handleDeleteFile = (index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedFiles(prev => prev.filter((_, i) => i !== index));
  };

  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      <div className="w-full max-w-4xl">
        <div className="flex items-start mb-4">
          <Link href="/">
            <Button variant="default" className="mt-4 bg-white text-black border-black hover:bg-black hover:text-white">
              Home
            </Button>
          </Link>
        </div>

        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Upload JSON Files
            </CardTitle>
            <p className="text-muted-foreground text-sm md:text-base">
              Drag & drop or click to select multiple JSON files
            </p>
          </CardHeader>

          <CardContent>
            <div
              className={`flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl ${isDragging ? "border-blue-500" : ""
                }`}
              onClick={handleFileClick}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <p className="text-center text-gray-500 mb-4">
                Drag & Drop your file here or click to select
              </p>
              <input
                ref={fileInputRef}
                type="file"
                className="hidden mb-4"
                multiple
                accept=".json"
                onChange={handleFileChange}
              />

              {selectedFiles.length > 0 && (
                <div className="mt-4 mb-8 text-center">
                  <p className="text-lg text-gray-700">Selected Files:</p>
                  {selectedFiles.map((file, idx) => (
                    <div key={idx} className="flex items-center justify-between text-gray-500 pb-3">
                      <span className="text-gray-700">{file.name} ({(file.size / 1024).toFixed(1)} KB)</span>
                      <Button
                        variant="destructive"
                        className="ml-2"
                        onClick={(e) => handleDeleteFile(idx, e)}
                      >
                        Delete
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <Button onClick={handleUpload} variant="default" className="mt-4 w-full">
              Upload
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default UploadJSON;
