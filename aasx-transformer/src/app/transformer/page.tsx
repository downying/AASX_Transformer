"use client";

import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import Link from "next/link";
import { listUploadedFiles, getSHA256HashesMap } from "@/lib/api/fileUpload";
import { downloadFile } from "@/lib/api/fileDownload";

const TransformerPage = () => {
    // 원래 업로드된 파일 목록
    const [uploadedFiles, setUploadedFiles] = useState<string[]>([]);
    // 최종 파일명(해시값) 리스트
    const [finalFileNames, setFinalFileNames] = useState<string[]>([]);

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

    // SHA256 해시값(최종 파일명) 로드
    useEffect(() => {
        const loadFinalFileNames = async () => {
            try {
                const data = await getSHA256HashesMap();
                // data의 key가 최종 파일명(해시값)이므로 이를 리스트로 변환
                setFinalFileNames(Object.keys(data));
            } catch (error) {
                console.error("최종 파일명을 가져오는 중 오류 발생:", error);
            }
        };

        loadFinalFileNames();
    }, []);

    // 개별 다운로드
    const handleDownload = async (hash: string) => {
        try {
            await downloadFile(hash);
            alert(`파일 다운로드 완료`);
        } catch (error) {
            console.error("다운로드 실패:", error);
            alert("다운로드에 실패했습니다.");
        }
    };

    // 전체 다운로드
    const handleDownloadAll = async () => {
        try {
            for (const hash of finalFileNames) {
                await downloadFile(hash);
            }
            alert("전체 파일 다운로드 완료");
        } catch (error) {
            console.error("전체 다운로드 실패:", error);
            alert("전체 파일 다운로드에 실패했습니다.");
        }
    };

    return (
        <div className="flex flex-col items-center justify-center w-full h-full bg-background text-foreground">
            <div className="w-full max-w-4xl">
                {/* 상단 홈 버튼 */}
                <div className="flex items-start mb-4">
                    <Link href="/">
                        <Button className="mt-4 bg-white text-black border border-black hover:bg-black hover:text-white">
                            Home
                        </Button>
                    </Link>
                </div>

                {/* 메인 카드 */}
                <Card>
                    <CardHeader className="text-center">
                        <CardTitle className="text-2xl md:text-3xl font-semibold">
                            Uploaded AASX Files
                        </CardTitle>
                        <p className="text-muted-foreground text-sm md:text-base">
                            for URL download in JSON format
                        </p>
                    </CardHeader>

                    <CardContent>
                        {/* 원래 업로드된 파일 목록 */}
                        <div className="flex flex-col items-center justify-center p-12 border-2 border-dashed border-border rounded-lg bg-muted w-full max-w-5xl">
                            {uploadedFiles.length > 0 ? (
                                <div className="mt-4 text-center">
                                    <p className="text-lg text-gray-700 mb-4">Original Uploaded Files:</p>
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

                        {/* 최종 파일명 리스트 뷰 (해시값 사용) */}
                        <div className="mt-4 flex flex-col items-center">
                            {finalFileNames.length > 0 ? (
                                <div className="w-full border p-4 rounded-lg">
                                    <h2 className="text-xl font-semibold mb-2">Final Files</h2>
                                    <ul className="space-y-2">
                                        {finalFileNames.map((hash, idx) => (
                                            <li
                                                key={idx}
                                                className="flex items-center justify-between text-lg text-gray-600"
                                            >
                                                <span>{hash}</span>
                                                <Button
                                                    onClick={() => handleDownload(hash)}
                                                    variant="outline"
                                                    size="sm"
                                                >
                                                    Download
                                                </Button>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            ) : (
                                <p className="text-gray-500">No final file names available.</p>
                            )}
                        </div>

                        {/* 전체 다운로드 버튼 */}
                        <Button onClick={handleDownloadAll} variant="default" className="mt-4 w-full">
                            Download All
                        </Button>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
};

export default TransformerPage;
