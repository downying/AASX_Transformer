"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { downloadFile, previewFile, deleteAttachmentMeta, fetchAllFileMetas } from "@/lib/api/fileDownload";

// 서버에서 받아올 파일 정보 타입 정의
export interface FileMeta {
  aasId: string;
  submodelId: string;
  idShort: string;
  name: string;
  extension: string;
  contentType: string;
  hash: string;
}

// 복합키 생성 함수
const generateCompositeKey = (meta: FileMeta) =>
  `${meta.aasId}::${meta.submodelId}::${meta.idShort}`;

export default function FileMetaPage() {
  const [data, setData] = useState<FileMeta[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]); // 복합키 기준 저장

  const [currentPage, setCurrentPage] = useState(0); // 페이지 번호
  const [pageSize] = useState(20);                  // 한 페이지당 항목 수
  const [totalCount, setTotalCount] = useState(0);  // 전체 개수

  // 파일 목록 로드
  useEffect(() => {
    loadMeta();
  }, [currentPage]);

  // 파일 메타 데이터 불러오기
  const loadMeta = async () => {
    try {
      const res = await fetchAllFileMetas(currentPage * pageSize, pageSize);
      setData(res.items);
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
  const handleAttachmentCheckboxChange = (meta: FileMeta, checked: boolean) => {
    const key = generateCompositeKey(meta);
    if (checked) {
      setSelectedKeys(prev => [...prev, key]);
    } else {
      setSelectedKeys(prev => prev.filter(k => k !== key));
    }
  };

  // 전체 선택/해제 핸들러
  const handleSelectAllAttachments = (checked: boolean) => {
    if (checked) {
      setSelectedKeys(data.map(meta => generateCompositeKey(meta)));
    } else {
      setSelectedKeys([]);
    }
  };

  // 선택한 파일들 다운로드
  const handleBatchDownloadAttachments = () => {
    if (selectedKeys.length === 0) {
      alert("선택된 파일이 없습니다.");
      return;
    }

    selectedKeys.forEach(key => {
      const meta = data.find(m => generateCompositeKey(m) === key);
      if (meta) {
        downloadFile(meta.hash + meta.extension);
      }
    });
  };

  // 삭제 핸들러
  const handleDelete = async (meta: FileMeta) => {
    if (!window.confirm("정말로 이 메타를 삭제하시겠습니까?")) return;
    try {
      const compositeKey = generateCompositeKey(meta);
      await deleteAttachmentMeta(compositeKey);
      await loadMeta(); // 삭제 후 목록 새로고침
    } catch (e) {
      console.error("삭제 실패:", e);
      alert("삭제 중 오류가 발생했습니다.");
    }
  };

  if (error) {
    return <div className="p-6 text-red-500">Error: {error}</div>;
  }

  return (
    <div className="p-6 overflow-visible">
      {/* 상단 바 */}
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-bold">All File Metadata</h2>
        <div className="flex gap-2">
          <Link href="/admin/uploaded">
            <Button
              variant="default"
              className="bg-blue-100 hover:bg-blue-200 cursor-pointer font-bold text-black border"
            >
              View Uploaded Files
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
                checked={selectedKeys.length === data.length && data.length > 0}
                onChange={(e) => handleSelectAllAttachments(e.target.checked)}
              />
            </th>
            <th className="px-4 py-2 border-b">Preview</th>
            <th className="px-4 py-2 border-b">AAS Id</th>
            <th className="px-4 py-2 border-b">Submodel Id</th>
            <th className="px-4 py-2 border-b">IdShort</th>
            <th className="px-4 py-2 border-b">Name</th>
            <th className="px-4 py-2 border-b">Ext</th>
            <th className="px-4 py-2 border-b">Content Type</th>
            <th className="px-4 py-2 border-b">Hash</th>
            <th className="px-4 py-2 border-b">Action</th>
          </tr>
        </thead>

        <tbody className="divide-y divide-gray-200">
          {data.map((meta, idx) => {
            const key = generateCompositeKey(meta);
            return (
              <tr key={idx} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-center">
                  <input
                    type="checkbox"
                    checked={selectedKeys.includes(key)}
                    onChange={(e) => handleAttachmentCheckboxChange(meta, e.target.checked)}
                  />
                </td>
                <td className="px-4 py-2 text-center">
                  <Button size="sm" variant="outline" onClick={() => previewFile(meta)}>
                    View
                  </Button>
                </td>
                <td className="px-4 py-2 break-all">{meta.aasId}</td>
                <td className="px-4 py-2 break-all">{meta.submodelId}</td>
                <td className="px-4 py-2">{meta.idShort}</td>
                <td className="px-4 py-2">{meta.name}</td>
                <td className="px-4 py-2">{meta.extension}</td>
                <td className="px-4 py-2">{meta.contentType}</td>
                <td className="px-4 py-2 font-mono text-sm break-all">{meta.hash}</td>
                <td className="px-4 py-2">
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => handleDelete(meta)}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            );
          })}
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
