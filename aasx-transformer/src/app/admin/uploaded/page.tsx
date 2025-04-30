"use client";

import React, { useState, useEffect } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { downloadFile, fetchUploadedFiles, previewFile } from '@/lib/api/fileDownload'

// 서버에서 받아올 파일 정보 타입 정의
export interface FileEntry {
  hash: string            // 파일 해시
  refCount: number        // 참조 횟수
  size: number            // 파일 크기 (bytes)
  extension: string       // 파일 확장자 (.png, .pdf 등)
  contentType: string     // 파일 MIME 타입
}

export default function UploadedPage() {
  const [error, setError] = useState<string | null>(null);
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [selectedFiles, setSelectedFiles] = useState<FileEntry[]>([]);

  const [currentPage, setCurrentPage] = useState(0); // 페이지 번호
  const [pageSize] = useState(20);                  // 한 페이지당 항목 수
  const [totalCount, setTotalCount] = useState(0);  // 전체 개수

  // 파일 목록 로드
  useEffect(() => {
    loadFiles();
  }, [currentPage]);

  // 해시값 기준 파일 데이터 불러오기
  const loadFiles = async () => {
    try {
      const res = await fetchUploadedFiles(currentPage * pageSize, pageSize);
      setFiles(res.items);
      setTotalCount(res.totalCount);
    } catch (e: any) {
      setError(e.message || "Unknown error");
    }
  };

  // 페이지 변경 핸들러
  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  // 개별 체크박스 핸들러
  const handleAttachmentCheckboxChange = (file: FileEntry, checked: boolean) => {
    if (checked) {
      setSelectedFiles(prev => [...prev, file]);
    } else {
      setSelectedFiles(prev => prev.filter(f => f.hash !== file.hash));
    }
  };

  // 전체 선택/해제 핸들러
  const handleSelectAllAttachments = (checked: boolean) => {
    if (checked) {
      setSelectedFiles(files);
    } else {
      setSelectedFiles([]);
    }
  };

  // 선택한 파일들 다운로드
  const handleBatchDownloadAttachments = () => {
    if (selectedFiles.length === 0) {
      alert("선택된 파일이 없습니다.");
      return;
    }

    selectedFiles.forEach(file => {
      downloadFile(file.hash + file.extension);
    });
  };

  return (
    <div className="p-6 overflow-visible">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-bold">Uploaded Files</h2>
        <div className="flex gap-2">
          <Link href="/admin/file-meta">
            <Button
              variant="default"
              className="bg-green-100 hover:bg-green-200 cursor-pointer font-bold text-black border"
            >
              View File Metadata
            </Button>
          </Link>
          <Button
            variant="default"
            className="bg-green-500 hover:bg-green-600 text-white font-bold"
            onClick={handleBatchDownloadAttachments}
          >
            Download Selected
          </Button>
        </div>
      </div>

      {/* 파일 리스트 테이블 */}
      <table className="min-w-full bg-white border border-gray-200">
        <thead className="bg-gray-100">
          <tr>
            <th className="px-4 py-2 border-b text-center">
              <input
                type="checkbox"
                checked={selectedFiles.length === files.length && files.length > 0}
                onChange={(e) => handleSelectAllAttachments(e.target.checked)}
              />
            </th>
            <th className="px-4 py-2 border-b">Preview</th>
            <th className="px-4 py-2 border-b">Hash</th>
            <th className="px-4 py-2 border-b">Reference Count</th>
            <th className="px-4 py-2 border-b">Size (bytes)</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {files.map((file, idx) => (
            <tr key={idx} className="hover:bg-gray-50">
              <td className="px-4 py-2 text-center">
                <input
                  type="checkbox"
                  checked={selectedFiles.some(m => m.hash === file.hash)}
                  onChange={(e) => handleAttachmentCheckboxChange(file, e.target.checked)}
                />
              </td>
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

      {/* Pagination */}
      <div className="mt-4 flex justify-center gap-2">
        {Array.from({ length: Math.ceil(totalCount / pageSize) }).map((_, i) => (
          <Button
            key={i}
            size="sm"
            variant={i === currentPage ? "default" : "outline"}
            onClick={() => handlePageChange(i)}
          >
            {i + 1}
          </Button>
        ))}
      </div>
    </div>
  );
}
