"use client";

import React, { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import axios from 'axios'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { fetchUploadedFiles, previewFile } from '@/lib/api/fileDownload'

// 서버에서 받아올 파일 정보 타입 정의
export interface FileEntry {
  hash: string            // 파일 해시
  refCount: number        // 참조 횟수
  size: number            // 파일 크기 (bytes)
  extension: string       // 파일 확장자 (.png, .pdf 등)
  contentType: string     // 파일 MIME 타입
}

export default function UploadedPage() {
  const [files, setFiles] = useState<FileEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  // 파일 목록 로드
  useEffect(() => {
    loadFiles()
  }, [])

  const loadFiles = async () => {
      try {
        const fileList = await fetchUploadedFiles();
        setFiles(fileList);
      } catch (e: any) {
        setError(e.message || "Unknown error");
      }
    };

  if (error) {
    return <div className="p-6 text-red-500">Error: {error}</div>
  }

  return (
    <div className="p-6 overflow-visible">
      <div className="flex justify-between items-center mb-4">
        <div className="flex items-center space-x-2">
          <h2 className="text-2xl font-bold">Uploaded Files</h2>
        </div>
        <Link href="/admin/file_meta">
          <Button
            variant="default"
            className="bg-green-100 hover:bg-green-200 cursor-pointer font-bold text-black border"
          >
            File Metadata
          </Button>
        </Link>
      </div>

      {/* 파일 리스트 테이블 */}
      <table className="min-w-full bg-white border border-gray-200">
        <thead className="bg-gray-100">
          <tr>
            <th className="px-4 py-2 border-b">Preview</th>
            <th className="px-4 py-2 border-b">Hash</th>
            <th className="px-4 py-2 border-b">Reference Count</th>
            <th className="px-4 py-2 border-b">Size (bytes)</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {files.map((file, idx) => (
            <tr key={idx} className="hover:bg-gray-50">
              {/* 미리보기 버튼 */}
              <td className="px-4 py-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() =>
                    previewFile({
                      hash: file.hash,
                      extension: file.extension || "",
                      contentType: file.contentType || "",
                    })
                  }
                >
                  View
                </Button>
              </td>

              <td className="px-4 py-2 break-all">{file.hash}</td>
              <td className="px-4 py-2 text-center">{file.refCount}</td>
              <td className="px-4 py-2 text-right">{file.size}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
