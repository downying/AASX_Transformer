"use client";

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { uploadJsonFiles } from '@/lib/api/fileUpload';
import { Environment } from '@/lib/api/Environment';

export default function UploadJSON() {
  const router = useRouter();
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // JSON 체크 헬퍼
  const isJson = (file: File) => file.name.toLowerCase().endsWith('.json');

  // 파일 선택
  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!event.target.files) return;
    const incoming = Array.from(event.target.files);

    // 확장자 체크
    const bad = incoming.find(f => !isJson(f));
    if (bad) {
      alert('JSON 파일이 아닙니다.');
      return;
    }

    setSelectedFiles(prev => [...prev, ...incoming]);
  };

  // 드래그 앤드 드랍 파일 선택
  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  // 드래그된 파일이 영역을 벗어났을 때 발생
  // 지정된 영역 밖으로 파일을 내놓을 때 발생하는 이벤트
  const handleDragLeave = () => setIsDragging(false);

  // 파일이 지정된 영역에 드랍되었을 때 발생
  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);

    if (event.dataTransfer.files && event.dataTransfer.files.length > 0) {
      const filesArray = Array.from(event.dataTransfer.files);

      // 확장자 검사
      const bad = filesArray.find((f) => !isJson(f));
      if (bad) {
        alert('JSON 파일이 아닙니다.');
        return;
      }

      // 모두 .json 면 상태에 추가
      setSelectedFiles((prevFiles) => [...prevFiles, ...filesArray]);
      console.log("드래그앤드랍으로 선택된 파일 : ", filesArray);
      console.log("선택한 파일 (상태 업데이트 전):", selectedFiles);
    }
  };

  // 업로드 후 input 값 초기화
  useEffect(() => {
    if (!selectedFiles.length && fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, [selectedFiles]);

  // 파일 업로드
  const handleUpload = async () => {
    if (!selectedFiles.length) {
      console.log("파일이 선택되지 않았습니다.");
      alert("파일을 선택하세요.");
      return;
    }

    const formData = new FormData();
    selectedFiles.forEach(file => formData.append('files', file));
    console.log("formData : ", formData);

    try {
      const response = await uploadJsonFiles(formData);
      console.log("서버 응답: ", response);
      if (response.length !== selectedFiles.length) {
        throw new Error(`파싱된 Environment 개수가 일치하지 않습니다: ${response.length}/${selectedFiles.length}`);
      }
      alert(`업로드 성공`);

      // 선택한 파일 목록 초기화
      setSelectedFiles([]); // 업로드 완료 후 초기화

      // /transformer 페이지로 이동
      router.push("/transformer/JsonToAasx");
    } catch (error) {
      console.error("업로드 오류: ", error);
      alert("업로드 실패");
    }
  };

  // 파일 선택을 위한 클릭 이벤트 처리
  const handleFileClick = (event: React.MouseEvent) => {
    event.stopPropagation(); // 클릭 이벤트 전파 막기
    if (fileInputRef.current) {
      fileInputRef.current.click(); // 파일 선택 창을 클릭하게 만듦
    }
  };

  // 파일 삭제 기능
  const handleDeleteFile = (index: number, event: React.MouseEvent) => {
    event.stopPropagation(); // 삭제 버튼 클릭 시 파일 선택창이 뜨지 않도록 방지

    // 선택한 파일을 삭제
    setSelectedFiles((prevFiles) => prevFiles.filter((_, i) => i !== index));
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
              className={`flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl ${isDragging ? "border-blue-500" : ""}`}
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
                onChange={handleFileChange}
                className="hidden mb-4"
                multiple
              />

              {selectedFiles.length > 0 && (
                <div className="mt-4 mb-8 text-center">
                  <p className="text-lg text-gray-700">Selected Files:</p>
                  {selectedFiles.map((file, index) => (
                    <div key={index} className="flex items-center justify-between text-gray-500 pb-3">
                      <span className="text-gray-700">{file.name} ({(file.size / 1024).toFixed(1)} KB)</span>
                      <Button
                        variant="destructive"
                        className="ml-2"
                        onClick={(event) => handleDeleteFile(index, event)}
                      >Delete</Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <Button
              onClick={handleUpload}
              variant="default"
              className="mt-4 w-full"
            >
              Upload
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
