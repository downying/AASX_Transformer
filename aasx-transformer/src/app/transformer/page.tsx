"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles, getReferencedFilePaths } from "@/lib/api/fileUpload";

const TransformerPage = () => {
    const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);
    const [referencedPaths, setReferencedPaths] = useState<string[]>([]);

    // 파일 목록 가져오기
    useEffect(() => {
        const loadFiles = async () => {
            try {
                const files = await listUploadedFiles(); // 비동기 API 호출
                setUploadedFiles(files); // 파일 상태 업데이트
            } catch (error) {
                console.error("파일 목록을 가져오는 중 오류 발생:", error);
            }
        };

        loadFiles();
    }, []);  // 빈 배열을 넣어 처음 마운트될 때만 실행

    // AASX 파일 경로 가져오기
    const handleGetReferencedPaths = async () => {
        try {
            const allReferencedPaths: string[] = [];
            
            for (const file of uploadedFiles) {
                const paths = await getReferencedFilePaths(file);
                
                // paths가 배열이 아닐 경우 빈 배열로 처리
                if (Array.isArray(paths)) {
                    allReferencedPaths.push(...paths);
                } else {
                    console.warn(`파일 ${file}에서 반환된 경로가 배열이 아닙니다.`, paths);
                }
            }

            setReferencedPaths(allReferencedPaths);  // 참조된 파일 경로 상태 업데이트
            console.log("All Referenced Paths:", allReferencedPaths); // 콘솔에 한 번만 출력

        } catch (error) {
            console.error("파일 경로를 가져오는 중 오류 발생:", error);
        }
    };

    return (
        <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
            <div className="w-full max-w-4xl">
                <div className="flex items-start mb-4">
                    <Link href="/">
                        <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white" variant="default">
                            Home
                        </Button>
                    </Link>
                </div>
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
                        <Button
                            onClick={handleGetReferencedPaths}  // 버튼 클릭 시 모든 파일에 대해 경로를 한 번만 가져옴
                            variant="default"
                            className="mt-4 w-full">
                            Get Referenced Paths for All Files
                        </Button>

                        {referencedPaths.length > 0 && (
                            <div className="mt-4 text-center">
                                <p className="text-lg text-gray-700 mb-4">Referenced Paths:</p>
                                <ul className="text-gray-600 space-y-2">
                                    {referencedPaths.map((path, index) => (
                                        <li key={index} className="text-lg">
                                            {path}
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        <Button onClick={() => alert("파일 변환을 시작합니다.")} variant="default" className="mt-4 w-full">
                            Transform
                        </Button>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
};

export default TransformerPage;
