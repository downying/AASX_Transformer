"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import {
    listUploadedFiles, getReferencedInMemoryFiles,
    InMemoryFile
} from "@/lib/api/fileUpload";

const TransformerPage = () => {
    // 업로드된 파일 이름 목록
    const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);
    // 인메모리 파일 (파일명 -> InMemoryFile[] 목록)
    const [inMemoryFilesMap, setInMemoryFilesMap] = useState<{
        [fileName: string]: InMemoryFile[];
    }>({});

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

    // 인메모리 파일(InMemoryFile) 가져오기
    const handleLoadInMemoryFiles = async () => {
        try {
            const data = await getReferencedInMemoryFiles();
            setInMemoryFilesMap(data);
        } catch (error) {
            console.error("인메모리 파일을 가져오는 중 오류 발생:", error);
        }
    };

    const handleTransform = () => {
        alert("파일 변환을 시작합니다.");
        // 변환 API 호출 로직 추가 가능
    };

    return (
        <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
            <div className="w-full max-w-4xl">
                {/* 상단 홈 버튼 */}
                <div className="flex items-start mb-4">
                    <Link href="/">
                        <Button
                            className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white"
                            variant="default"
                        >
                            Home
                        </Button>
                    </Link>
                </div>

                {/* 메인 카드 */}
                <Card>
                    <CardHeader className="text-center">
                        <CardTitle className="text-2xl md:text-3xl font-semibold">
                            Upload the AASX package file
                        </CardTitle>
                        <p className="text-muted-foreground text-sm md:text-base">
                            for URL download in JSON format
                        </p>
                    </CardHeader>

                    <CardContent>
                        {/* 업로드된 파일 목록 */}
                        <div className="flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl">
                            {uploadedFiles.length > 0 ? (
                                <div className="mt-4 text-center">
                                    <p className="text-lg text-gray-700 mb-4">Uploaded Files:</p>
                                    <ul className="text-gray-600 space-y-2">
                                        {uploadedFiles.map((file, index) => (
                                            <li key={index} className="text-lg">
                                                {file}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            ) : (
                                <p className="text-center text-gray-500">No files uploaded.</p>
                            )}
                        </div>

                        {/* InMemoryFile 불러오기 버튼 */}
                        <div className="mt-4 flex flex-col items-center">
                            <Button
                                onClick={handleLoadInMemoryFiles}
                                variant="default"
                                className="mb-4"
                            >
                                Load InMemory Files
                            </Button>

                            {/* 인메모리 파일 표시 */}
                            {Object.keys(inMemoryFilesMap).length > 0 && (
                                <div className="w-full border p-4 rounded-lg">
                                    <h2 className="text-xl font-semibold mb-2">
                                        InMemoryFile Map
                                    </h2>
                                    {Object.entries(inMemoryFilesMap).map(
                                        ([fileName, inMemoryFiles]) => (
                                            <div key={fileName} className="mb-6">
                                                <h3 className="text-lg font-bold mb-2">{fileName}</h3>
                                                {inMemoryFiles.length === 0 ? (
                                                    <p className="text-gray-500">No in-memory files.</p>
                                                ) : (
                                                    inMemoryFiles.map((file, idx) => (
                                                        <div key={idx} className="p-2 border-b">
                                                            <p className="text-sm text-gray-600">
                                                                Path: {file.path}
                                                            </p>

                                                            <img src={`data:image/png;base64,${file.fileContent}`} alt="preview" />
                                                            <p className="text-sm text-gray-500">
                                                                Base64 Content :{" "}
                                                                {file.fileContent ? file.fileContent.slice(0, 50) : "No content available"}...
                                                            </p>

                                                        </div>
                                                    ))
                                                )}
                                            </div>
                                        )
                                    )}
                                </div>
                            )}
                        </div>

                        {/* 변환 버튼 (임시) */}
                        <Button
                            onClick={() => alert("파일 변환을 시작합니다.")}
                            variant="default"
                            className="mt-4 w-full"
                        >
                            Transform
                        </Button>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
};

export default TransformerPage;
