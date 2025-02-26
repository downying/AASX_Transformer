"use client";

import React, { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { uploadFile } from "@/lib/api/fileUpload";
import Link from "next/link";

const MainPage = () => {

  // selectedFile : 사용자가 선택한 파일을 저장
  // setSelectedFile : 파일을 업데이트
  // 초깃값은 null, 파일이 선택되면 파일 객체가 저장
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  // drag and drop
  const [isDragging, setIsDragging] = useState(false);
  // 파일 선택 input을 참조하기 위한 ref
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // 파일 선택
  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files.length > 0) {
      const file = event.target.files[0];
      setSelectedFile(file);
      console.log("선택한 파일 : ", file); 
      console.log("선택한 파일 (상태 업데이트 전):", selectedFile);
    }
  };

  // 드래그 앤드 드랍 파일 선택
  const handleDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    // 기본적으로, HTML 요소 위로 파일을 드래그할 경우, 
    // 브라우저는 해당 파일을 열거나 다운로드하려고 시도하는데 이를 방지
    event.preventDefault(); 
    // 사용자가 파일을 드래그 중인 상태임
    setIsDragging(true);
  };

  // 드래그된 파일이 영역을 벗어났을 때 발생
  // 지정된 영역 밖으로 파일을 내놓을 때 발생하는 이벤트
  const handleDragLeave = () => {
    // 드래그 상태를 false로 설정
    setIsDragging(false);
  };

  // 파일이 지정된 영역에 드랍되었을 때 발생
  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    // 드래그 상태를 종료
    setIsDragging(false);
    if (event.dataTransfer.files && event.dataTransfer.files.length > 0) {
      const file = event.dataTransfer.files[0];
      setSelectedFile(file);
      console.log("드래그앤드랍으로 선택된 파일 : ", file);
      console.log("선택한 파일 (상태 업데이트 전):", selectedFile);
    }
  };

  // selectedFile이 setState후 정상적으로 반영되었는지 확인
  useEffect(() => {
    if (selectedFile) {
      console.log("선택한 파일 (상태 업데이트 후):", selectedFile);
    }
  }, [selectedFile]);

  // 파일 업로드
  const handleUpload = async () => {
    if (!selectedFile) {
      // 파일이 선택되지 않았을 때 로그 출력
    console.log("파일이 선택되지 않았습니다.");
      alert("파일을 선택하세요.");
      return; // 업로드 요청을 아예 실행하지 않음
    }

    // 웹 애플리케이션에서 파일을 업로드할 때는 multipart/form-data 형식이 필요
    const formData = new FormData();
    formData.append("file", selectedFile);
    console.log(formData);

    try {
      const response = await uploadFile(formData);
      alert(response); 
      console.log("파일 업로드 : ", response)
    } catch (error) {
      alert("업로드 실패");
    }
  };

   // 파일 선택을 위한 클릭 이벤트 처리
   const handleFileClick = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click(); // 파일 선택 input을 클릭하게 만듦
    }
  };

  return (
    <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
      {/* 전체를 하나의 div로 묶기 */}
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
          {/* 헤더 */}
          <CardHeader className="text-center">
            <CardTitle className="text-2xl md:text-3xl font-semibold">
              Upload the AASX package file
            </CardTitle>
            <p className="text-muted-foreground text-sm md:text-base">
              for URL download in JSON format
            </p>
          </CardHeader>
          {/* 업로드 영역 */}
          <CardContent>
            <div
              className={`flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl ${
                isDragging ? "border-blue-500" : ""
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
                onChange={handleFileChange}
                className="hidden mb-4"
                accept=".aasx" // 특정 확장자만 허용 가능
              />
              {/* 선택된 파일 정보 표시 */}
              {selectedFile && (
                <div className="mt-4 mb-8 text-center">
                  <p className="text-lg text-gray-700">Selected File:</p>
                  <p className="text-gray-500">{selectedFile.name}</p>
                  <p className="text-gray-400 text-sm">
                    Size: {(selectedFile.size / 1024).toFixed(2)} KB
                  </p>
                </div>
              )}
              <Button onClick={handleUpload} variant="default">
                Upload
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default MainPage;
