"use client";

import React, { useState, useEffect } from "react"
import axios from "axios"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { previewFile } from "@/lib/api/fileDownload"

// 환경 변수로 지정된 API 엔드포인트
const API_URL = process.env.NEXT_PUBLIC_API_URL || ""

// 서버에서 받아올 파일 메타 정보 타입 정의
export interface FileMeta {
  aasId: string      // AAS 식별자
  submodelId: string // Submodel 식별자
  idShort: string    // 파일 요소 idShort
  name: string       // 사용자 표시용 이름
  extension: string  // 파일 확장자
  contentType: string// 파일 MIME 타입
  hash: string       // 파일 해시
}

export default function FileMetaPage() {
  const [data, setData] = useState<FileMeta[]>([])
  const [error, setError] = useState<string | null>(null)

  // 메타 데이터 가져오기
  useEffect(() => {
    fetchMeta()
  }, [])

  const fetchMeta = async () => {
    try {
      const response = await axios.get<FileMeta[]>(`${API_URL}/api/transformer/file-metas`)
      setData(response.data)
    } catch (e: any) {
      setError(e.message || "Unknown error")
    }
  }

  // 삭제 핸들러
  const handleDelete = async (meta: FileMeta) => {
    if (!window.confirm("정말로 이 메타를 삭제하시겠습니까?")) return
    try {
      const compositeKey = `${meta.aasId}::${meta.submodelId}::${meta.idShort}`
      await axios.delete(`${API_URL}/api/transformer/delete/file`, { params: { compositeKey } })
      // 삭제 후 목록 갱신
      fetchMeta()
    } catch (e) {
      console.error("삭제 실패:", e)
      alert("삭제 중 오류가 발생했습니다.")
    }
  }

  if (error) {
    return <div className="p-6 text-red-500">Error: {error}</div>
  }

  return (
    <div className="p-6 overflow-visible">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-bold">All File Metadata</h2>
        <Link href="/admin/uploaded">
          <Button variant="default" className="bg-blue-100 hover:bg-blue-200 cursor-pointer font-bold text-black border">
            Uploaded Files
          </Button>
        </Link>
      </div>

      <table className="min-w-full bg-white border border-gray-200">
        <thead className="bg-gray-100">
          <tr>
            <th className="px-4 py-2 border-b">Preview</th>
            <th className="px-4 py-2 border-b">AAS ID</th>
            <th className="px-4 py-2 border-b">Submodel ID</th>
            <th className="px-4 py-2 border-b">Id Short</th>
            <th className="px-4 py-2 border-b">Name</th>
            <th className="px-4 py-2 border-b">Ext</th>
            <th className="px-4 py-2 border-b">Content Type</th>
            <th className="px-4 py-2 border-b">Hash</th>
            <th className="px-4 py-2 border-b">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {data.map((meta, idx) => (
            <tr key={idx} className="hover:bg-gray-50">
              <td className="px-4 py-2">
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
                <Button size="sm" variant="destructive" onClick={() => handleDelete(meta)}>
                  Delete
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
