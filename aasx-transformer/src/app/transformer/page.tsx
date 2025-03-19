"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles, getReferencedFilePaths } from "@/lib/api/fileUpload";

const TransformerPage = () => {
    const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);
    const [referencedPaths, setReferencedPaths] = useState<{ [key: string]: string[] }>({});

    // 파일 목록 가져오기
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

    // AASX 파일에서 참조된 파일 경로 가져오기
    useEffect(() => {
        const loadReferencedPaths = async () => {
            try {
                const paths = await getReferencedFilePaths();
                setReferencedPaths(paths);
            } catch (error) {
                console.error("참조된 파일 경로를 가져오는 중 오류 발생:", error);
            }
        };

        loadReferencedPaths();
    }, []);

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

                        {/* 참조된 파일 경로 목록 */}
                        {Object.keys(referencedPaths).length > 0 && (
                            <div className="mt-4 text-center">
                                <p className="text-lg text-gray-700 mb-4">Referenced Paths by File:</p>
                                {Object.keys(referencedPaths).map(fileName => (
                                    <div key={fileName} className="mb-4">
                                        <h3 className="font-bold">{fileName}</h3>
                                        <ul className="text-gray-600 space-y-2">
                                            {referencedPaths[fileName].map((path, idx) => (
                                                <li key={idx} className="text-lg">
                                                    {path}
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                ))}
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
